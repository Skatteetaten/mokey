package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.reactive.awaitFirstOrNull
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.ResourceNotFoundException
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.ManagementCacheKey
import no.skatteetaten.aurora.mokey.model.ManagementEndpoint
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.toCacheKey
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Predicate

private val logger = KotlinLogging.logger {}

@Service
class OpenShiftManagementClient(
    @Qualifier("managmenetClient") val client: KubernetesReactorClient,
    @Value("\${mokey.management.cache:true}") val cacheManagement: Boolean
) {

    // Does this need to be an async cache?
    val cache: Cache<ManagementCacheKey, ManagementEndpointResult<*>> = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(100000)
        .build()

    suspend fun proxyManagementInterfaceRaw(endpoint: ManagementEndpoint): HttpResponse {

        val headers = if (endpoint.endpointType == EndpointType.HEALTH) {
            mapOf(HttpHeaders.ACCEPT to "application/vnd.spring-boot.actuator.v2+json,application/json")
        } else emptyMap()

        val call = client.proxyGet<String>(
            pod = endpoint.pod,
            port = endpoint.port,
            path = endpoint.path,
            headers = headers
        ).flatMap { Mono.just(HttpResponse(it, 200)) }

        if (endpoint.endpointType != EndpointType.HEALTH) {
            return call.awaitFirstOrNull() ?: throw ResourceNotFoundException("No response for url=${endpoint.url}")
        }

        return call.onErrorContinue(
                Predicate { it is WebClientResponseException && it.statusCode.is5xxServerError },
                BiConsumer { t, u ->
                    val wre = t as WebClientResponseException
                    logger.debug(
                        "Respone ${wre.statusCode.value()} error url=${endpoint.url} status=ERROR body={}",
                        wre.responseBodyAsString
                    )
                    HttpResponse(wre.responseBodyAsString, wre.statusCode.value())
                }
            ).timeout(
                Duration.ofSeconds(2),
                Mono.error(TimeoutException("Timed out getting management interface for url=${endpoint.url}"))
            )
            .awaitFirstOrNull() ?: throw ResourceNotFoundException("No response for url=${endpoint.url}")
    }

    fun clearCache() = cache.invalidateAll()

    final suspend inline fun <reified T : Any> getCachedOrFind(
        endpoint: ManagementEndpoint
    ): ManagementEndpointResult<T> {
        val logger = KotlinLogging.logger {}

        if (!cacheManagement) {
            logger.trace("cache disabled")
            return findJsonResource(endpoint)
        }

        val key = endpoint.toCacheKey()
        val cachedResponse = (cache.getIfPresent(key) as ManagementEndpointResult<T>?)?.also {
            logger.debug("Found cached response for $key")
        }
        return cachedResponse ?: findJsonResource<T>(endpoint).also {
            // TODO: do we need to fine tune this?
            if (!it.isSuccess && it.response?.code == 503) {
                logger.debug("There is a 503 error that does not succeed")
            } else {
                logger.debug("Cached management interface $key")
                cache.put(key, it)
            }
        }
    }

    /*
      What if we want to find prometheus endpoint here that is not json?

      Refactoring suggestion, make the proxy call return an exception or an success rather then doing the parsing here
     */
    final suspend inline fun <reified S : Any> findJsonResource(endpoint: ManagementEndpoint): ManagementEndpointResult<S> {

        // If health we should run the code above
        val logger = KotlinLogging.logger {}
        val response = try {
            proxyManagementInterfaceRaw(endpoint)
        } catch (e: WebClientResponseException) {
            // 401 this is an error
            logger.debug("Respone error url=${endpoint.url} status=ERROR body={}", e.responseBodyAsString)
            return toManagementEndpointResult(
                response = HttpResponse(e.responseBodyAsString, e.rawStatusCode),
                resultCode = "ERROR_HTTP",
                errorMessage = e.localizedMessage,
                endpoint = endpoint
            )
        } catch (e: Exception) {
            // When there is no response
            logger.debug("Respone other excpption url=${endpoint.url} status=ERROR body={}", e.message, e)
            return toManagementEndpointResultAsError(exception = e, endpoint = endpoint)
        }

        // this scales poorly if we want to call prometheus here, we should just get proxy method to return the body parsed
        val deserialized: S = try {
            jacksonObjectMapper().readValue(response.content)
        } catch (e: Exception) {
            // inavalid body
            logger.debug("Jackson serialization exception for url=${endpoint.url} message=${e.message}")
            return toManagementEndpointResultAsError(exception = e, response = response, endpoint = endpoint)
        }

        return toManagementEndpointResult(
            endpoint = endpoint,
            deserialized = deserialized,
            response = response,
            resultCode = "OK"
        )
    }

    fun <T : Any> toManagementEndpointResult(
        deserialized: T? = null,
        response: HttpResponse? = null,
        resultCode: String,
        errorMessage: String? = null,
        endpoint: ManagementEndpoint
    ): ManagementEndpointResult<T> {
        return ManagementEndpointResult(
            deserialized = deserialized,
            response = response,
            resultCode = resultCode,
            errorMessage = errorMessage,
            endpointType = endpoint.endpointType,
            url = endpoint.url
        )
    }

    fun <S : Any> toManagementEndpointResultAsError(
        exception: Exception,
        response: HttpResponse? = null,
        endpoint: ManagementEndpoint
    ): ManagementEndpointResult<S> {
        val resultCode = when (exception) {
            is JsonParseException -> "INVALID_JSON"
            is ResourceNotFoundException -> "ERROR_IO"
            is TimeoutException -> "TIMEOUT"
            else -> "ERROR_UNKNOWN"
        }

        return toManagementEndpointResult(
            response = response,
            resultCode = resultCode,
            errorMessage = exception.message,
            endpoint = endpoint
        )
    }
}
