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
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.TimeUnit

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

    suspend fun proxyManagementInterfaceRaw(endpoint: ManagementEndpoint): String {
        return client.proxyGet<String>(
                pod = endpoint.pod,
                port = endpoint.port,
                path = endpoint.path,
                headers = mapOf(HttpHeaders.ACCEPT to "application/vnd.spring-boot.actuator.v2+json,application/json")
            )
            .timeout(
                Duration.ofSeconds(2),
                Mono.error(TimeoutException("Timed out getting management interface for url=${endpoint.url}"))
            )
            .awaitFirstOrNull() ?: throw ResourceNotFoundException("No response forurl=${endpoint.url}")
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
            // TODO: Here we have to make sure that if this is an error that should not be cached we cannot cache it. IE network errors
            logger.debug("Cached management interface $key")
            cache.put(key, it)
        }
    }

    /*
      What if we want to find prometheus endpoint here that is not json?
     */
    final suspend inline fun <reified S : Any> findJsonResource(endpoint: ManagementEndpoint): ManagementEndpointResult<S> {

        val logger = KotlinLogging.logger {}
        val response = try {
            HttpResponse(proxyManagementInterfaceRaw(endpoint), 200)
        } catch (e: WebClientResponseException) {
            // This needs to be cleaned up after we have coverage of it all
            val errorResponse = HttpResponse(String(e.responseBodyAsByteArray), e.statusCode.value())
            if (e.statusCode.is5xxServerError) {
                // This is OK, it is not an error
                // 503
                logger.debug(
                    "Respone ${e.statusCode.value()} error url=${endpoint.url} status=ERROR body={}",
                    errorResponse.content
                )
                errorResponse
            } else {
                // 401 this is an error
                logger.debug("Respone error url=${endpoint.url} status=ERROR body={}", errorResponse.content)
                return toManagementEndpointResultAsError(exception = e, response = errorResponse, endpoint = endpoint)
            }
        } catch (e: Exception) {
            // When there is no response
            logger.debug("Respone other excpption url=${endpoint.url} status=ERROR body={}", e.message, e)
            return toManagementEndpointResultAsError(exception = e, endpoint = endpoint)
        }

        val deserialized: S = try {
            jacksonObjectMapper().readValue(response.content)
        } catch (e: Exception) {
            // inavalid body
            logger.debug("Jackson serialization exception=${e.message}")
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
        // JsonParseException
        val resultCode = when (exception) {
            is JsonParseException -> "INVALID_JSON"
            is WebClientResponseException -> "ERROR_HTTP"
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
