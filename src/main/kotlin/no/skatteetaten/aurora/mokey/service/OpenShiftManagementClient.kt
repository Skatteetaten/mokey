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
import java.util.concurrent.TimeUnit

@Service
class OpenShiftManagementClient(
    @Qualifier("managementClient") val client: KubernetesReactorClient,
    @Value("\${mokey.management.cache:true}") val cacheManagement: Boolean,
) {
    val cache: Cache<ManagementCacheKey, ManagementEndpointResult<*>> = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(100000)
        .build()

    suspend fun proxyManagementInterfaceRaw(endpoint: ManagementEndpoint): String {
        val call = client.proxyGet<String>(
            pod = endpoint.pod,
            port = endpoint.port,
            path = endpoint.path,
            headers = mapOf(HttpHeaders.ACCEPT to "application/vnd.spring-boot.actuator.v2+json,application/json")
        )

        return call.awaitFirstOrNull() ?: throw ResourceNotFoundException("No response for url=${endpoint.url}")
    }

    fun clearCache() = cache.invalidateAll()

    final suspend inline fun <reified T : Any> getCachedOrFind(
        endpoint: ManagementEndpoint,
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
            when {
                !it.isSuccess && it.response?.code == 503 -> {
                    logger.debug(
                        "We did not cache this reponse there is a 503 error that does not succeed url={} response={}",
                        endpoint.url,
                        it.response
                    )
                }
                else -> {
                    logger.debug("Cached management interface $key")

                    cache.put(key, it)
                }
            }
        }
    }

    /*
      What if we want to find prometheus endpoint here that is not json?

      Refactoring suggestion, make the proxy call return an exception or an success rather then doing the parsing here
     */
    final suspend inline fun <reified S : Any> findJsonResource(
        endpoint: ManagementEndpoint
    ): ManagementEndpointResult<S> {
        val logger = KotlinLogging.logger {}
        val response = runCatching {
            HttpResponse(proxyManagementInterfaceRaw(endpoint), 200)
        }.getOrElse {
            when (it) {
                is WebClientResponseException -> {
                    val errorResponse = HttpResponse(String(it.responseBodyAsByteArray), it.statusCode.value())

                    if (it.statusCode.is5xxServerError) {
                        // 503
                        logger.debug(
                            "WebClientResponse ${it.statusCode.value()} url=${endpoint.url} status=ERROR body={}",
                            errorResponse.content
                        )
                        // This is the management call succeeding but returning an error code in the 5x range, which is acceptable
                        errorResponse
                    } else {
                        // 401 this is an error
                        logger.info("Response error url=${endpoint.url} status=ERROR body={}", it.responseBodyAsString)

                        return toManagementEndpointResult(
                            response = HttpResponse(it.responseBodyAsString, it.rawStatusCode),
                            resultCode = "ERROR_HTTP",
                            errorMessage = it.localizedMessage,
                            endpoint = endpoint
                        )
                    }
                }
                else -> {
                    logger.debug("Response other exception url=${endpoint.url} status=ERROR body={}", it.message, it)

                    return toManagementEndpointResultAsError(exception = it as Exception, endpoint = endpoint)
                }
            }
        }
        val deserialized: S = runCatching<S> {
            jacksonObjectMapper().readValue(response.content)
        }.getOrElse {
            logger.debug(
                "Jackson serialization exception for " +
                    "url=${endpoint.url} " +
                    "content='${response.content}' " +
                    "message=${it.message}"
            )

            return toManagementEndpointResultAsError(
                exception = it as Exception,
                response = response,
                endpoint = endpoint
            )
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
        endpoint: ManagementEndpoint,
    ): ManagementEndpointResult<T> = ManagementEndpointResult(
        deserialized = deserialized,
        response = response,
        resultCode = resultCode,
        errorMessage = errorMessage,
        endpointType = endpoint.endpointType,
        url = endpoint.url,
    )

    fun <S : Any> toManagementEndpointResultAsError(
        exception: Exception,
        response: HttpResponse? = null,
        endpoint: ManagementEndpoint,
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
            endpoint = endpoint,
        )
    }
}
