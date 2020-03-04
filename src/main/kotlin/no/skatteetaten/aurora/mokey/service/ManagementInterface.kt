package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.api.model.Pod
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.reactive.function.client.WebClientResponseException

private val logger = KotlinLogging.logger {}

// TODO: This entire abstraction can go away. The client code can go into OpenShiftManagemetnClient
class ManagementEndpoint(val pod: Pod, val port: Int, val path: String, val endpointType: EndpointType) {

    val url = "namespaces/${pod.metadata.namespace}/pods/${pod.metadata.name}:$port/proxy/$path"

    // A lot of this error handling should be moved into the client
    fun <S : Any> findJsonResource(client: OpenShiftManagementClient, clazz: Class<S>): ManagementEndpointResult<S> {

        // logger.debug("Getting managementResource for uri={}", url)

        // TODO: Previously this had a timeout of 2s, what is it now?
        val response = try {
            val response = runBlocking { client.proxyManagementInterfaceRaw(pod, port, path) }
            // logger.debug("Response status=OK body={}", response)
            HttpResponse(response, 200)
        } catch (e: WebClientResponseException) {
            val errorResponse = HttpResponse(String(e.responseBodyAsByteArray), e.statusCode.value())
            // logger.debug("Respone url=$url status=ERROR body={}", errorResponse.content)
            if (e.statusCode.is5xxServerError) {
                errorResponse
            } else {
                return toManagementEndpointResultAsError(exception = e, response = errorResponse)
            }
        } catch (e: Exception) {
            return toManagementEndpointResultAsError(exception = e)
        }

        val deserialized = try {
            jacksonObjectMapper().readValue(response.content, clazz)
        } catch (e: Exception) {
            return toManagementEndpointResultAsError(exception = e, response = response)
        }

        return toManagementEndpointResultAsSuccess(deserialized = deserialized, response = response)
    }

    private fun <T : Any> toManagementEndpointResult(
        deserialized: T? = null,
        response: HttpResponse? = null,
        resultCode: String,
        errorMessage: String? = null
    ): ManagementEndpointResult<T> {
        return ManagementEndpointResult(
            deserialized = deserialized,
            response = response,
            resultCode = resultCode,
            errorMessage = errorMessage,
            endpointType = this.endpointType,
            url = this.url
        )
    }

    fun <T : Any> toManagementEndpointResultAsSuccess(
        deserialized: T?,
        response: HttpResponse?
    ): ManagementEndpointResult<T> =
        toManagementEndpointResult(
            deserialized = deserialized,
            response = response,
            resultCode = "OK"
        )

    // TODO: This has to be rewritten in the client
    fun <S : Any> toManagementEndpointResultAsError(
        exception: Exception,
        response: HttpResponse? = null
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
            errorMessage = exception.message
        )
    }
}
