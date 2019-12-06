package no.skatteetaten.aurora.mokey.service

import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.ServiceTypes
import no.skatteetaten.aurora.mokey.TargetService
import reactor.core.publisher.Mono
import reactor.core.publisher.toMono
import uk.q3c.rest.hal.HalResource
import java.time.Duration
import java.time.Instant

data class ImageTagResource(
    val auroraVersion: String? = null,
    val appVersion: String? = null,
    val timeline: ImageBuildTimeline,
    val dockerVersion: String,
    val dockerDigest: String,
    val java: JavaImage? = null,
    val node: NodeJsImage? = null,
    val requestUrl: String
) : HalResource()

data class ImageBuildTimeline(
    val buildStarted: Instant?,
    val buildEnded: Instant?
)

data class NodeJsImage(val nodeJsVersion: String)

data class JavaImage(
    val major: String,
    val minor: String,
    val build: String,
    val jolokia: String?
)

data class CantusFailure(
    val url: String,
    val errorMessage: String
)

data class AuroraResponse<T : HalResource?>(
    val items: List<T> = emptyList(),
    val failure: List<CantusFailure> = emptyList(),
    val success: Boolean = true,
    val message: String = "OK",
    val failureCount: Int = failure.size,
    val successCount: Int = items.size,
    val count: Int = failureCount + successCount
) : HalResource()

data class TagUrlsWrapper(val tagUrls: List<String>)

private val logger = KotlinLogging.logger { }

@Service
class ImageRegistryClient(
    @TargetService(ServiceTypes.CANTUS)
    val webClient: WebClient
) {

    fun post(path: String, body: Any): Mono<AuroraResponse<ImageTagResource>> {
        return execute {
            it.post().uri(path).body(BodyInserters.fromObject(body))
        }
    }

    private inline fun <reified T : Any> execute(
        fn: (WebClient) -> WebClient.RequestHeadersSpec<*>
    ): Mono<T> = fn(webClient)
        .retrieve()
        .bodyToMono<T>()
        .retryBackoff(3, Duration.ofMillis(200))
        .handleGenericError()

    private fun <T> Mono<T>.handleGenericError(): Mono<T> =
        this.handleError("cantus")
            .switchIfEmpty(ServiceException("Empty resource from Image Registry").toMono())

    fun <T> Mono<T>.handleError(sourceSystem: String?) =
        this.doOnError {
            when (it) {
                is WebClientResponseException -> {
                    val errorMessage = "Error in response, status=${it.rawStatusCode} message=${it.statusText}"
                    val message = it.readResponse() ?: errorMessage

                    throw ServiceException(
                        message = message,
                        cause = it
                    )
                }
                else -> throw ServiceException(
                    message = it.message ?: "",
                    cause = it
                )
            }
        }
}

private fun WebClientResponseException.readResponse(): String? {
    this.request?.let {
        logger.info { "Error request url:${it.uri.toASCIIString()}" }
    }

    val body = this.responseBodyAsString
    logger.debug { "Error response body: $body" }

    val json = JsonPath.parse(body, Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS))
    return json.read<String>("$.message") ?: json.read<String>("$.items[0]")
}
