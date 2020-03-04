package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.fabric8.kubernetes.api.model.Pod
import kotlinx.coroutines.reactive.awaitFirst
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpoint
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class OpenShiftManagementClient(
    @Qualifier("managmenetClient") val client: KubernetesReactorClient
) {
    suspend fun proxyManagementInterfaceRaw(pod: Pod, port: Int, path: String): String {
        return client.proxyGet<String>(
            pod = pod,
            port = port,
            path = path,
            headers = mapOf(HttpHeaders.ACCEPT to "application/vnd.spring-boot.actuator.v2+json,application/json")
        )
            .timeout(
                Duration.ofSeconds(2),
                Mono.error<String>(RuntimeException("Timed out getting management interface in namespace=${pod.metadata.namespace}  pod=${pod.metadata.name} path=$path for "))
            ).awaitFirst()
    }

    final suspend inline fun <reified S : Any> findJsonResource(endpoint: ManagementEndpoint): ManagementEndpointResult<S> {

        val response = try {
            val response = proxyManagementInterfaceRaw(endpoint.pod, endpoint.port, endpoint.path)
            // logger.debug("Response status=OK body={}", response)
            HttpResponse(response, 200)
        } catch (e: WebClientResponseException) {
            // THis needs to be cleaned up after we have coverage of it all
            val errorResponse = HttpResponse(String(e.responseBodyAsByteArray), e.statusCode.value())
            // logger.debug("Respone url=$url status=ERROR body={}", errorResponse.content)
            if (e.statusCode.is5xxServerError) {
                errorResponse
            } else {
                return toManagementEndpointResultAsError(exception = e, response = errorResponse, endpoint = endpoint)
            }
        } catch (e: Exception) {
            return toManagementEndpointResultAsError(exception = e, endpoint = endpoint)
        }

        val deserialized: S = try {
            jacksonObjectMapper().readValue(response.content)
        } catch (e: Exception) {
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
            is HttpStatusCodeException -> "ERROR_${exception.statusCode}"
            is RestClientException -> "ERROR_HTTP"
            is JsonParseException -> "INVALID_JSON"
            is MismatchedInputException -> "INVALID_JSON"
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

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscoveryResponse(
    val _links: Map<String, DiscoveryLink>
)

fun DiscoveryResponse.createEndpoint(pod: Pod, port: Int, type: EndpointType): ManagementEndpoint? {
    return this._links[type.key.toLowerCase()]?.path?.let {
        ManagementEndpoint(pod, port, it, type)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscoveryLink(
    val href: String
) {

    val path: String get() = href.replace("http://", "").substringAfter("/")
}

fun <T : Any> EndpointType.missingResult(): ManagementEndpointResult<T> {
    return ManagementEndpointResult(
        errorMessage = "Unknown endpoint link",
        endpointType = this,
        resultCode = "LINK_MISSING"
    )
}
