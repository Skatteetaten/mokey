package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.Endpoint.ENV
import no.skatteetaten.aurora.mokey.model.Endpoint.HEALTH
import no.skatteetaten.aurora.mokey.model.Endpoint.INFO
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Service
class ManagementEndpointFactory(val restTemplate: RestTemplate) {
    fun create(managementUrl: String): ManagementEndpoint {
        return ManagementEndpoint.create(restTemplate, managementUrl)
    }
}

class ManagementEndpointException(val endpoint: Endpoint, val errorCode: String, url: String? = null, cause: Exception? = null)
    : RuntimeException("${endpoint}_$errorCode", cause)

class ManagementEndpoint internal constructor(
    private val restTemplate: RestTemplate,
    val links: ManagementLinks
) {

    @Throws(ManagementEndpointException::class)
    fun getHealthEndpointResponse(): HealthResponse = findJsonResource(HEALTH, HealthResponse::class)

    @Throws(ManagementEndpointException::class)
    fun getInfoEndpointResponse(): InfoResponse = findJsonResource(INFO, InfoResponse::class)

    @Throws(ManagementEndpointException::class)
    fun getEnvEndpointResponse(): JsonNode = findJsonResource(ENV, JsonNode::class)

    private fun <T : Any> findJsonResource(endpoint: Endpoint, type: KClass<T>) =
            findJsonResource(restTemplate, endpoint, links.linkFor(endpoint), type)

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

        @Throws(ManagementEndpointException::class)
        fun create(restTemplate: RestTemplate, managementUrl: String): ManagementEndpoint {

            val response = findJsonResource(restTemplate, Endpoint.MANAGEMENT, managementUrl, JsonNode::class)
            val links = try {
                ManagementLinks.parseManagementResponse(response)
            } catch (e: Exception) {
                throw ManagementEndpointException(Endpoint.MANAGEMENT, "INVALID_FORMAT", managementUrl, e)
            }

            return ManagementEndpoint(restTemplate, links)
        }

        private fun <T : Any> findJsonResource(restTemplate: RestTemplate, endpoint: Endpoint, url: String, type: KClass<T>): T {

            logger.debug("Getting resource with url={}", url)
            try {
                val responseText: String = try {
                    restTemplate.getForObject(url, String::class.java)
                } catch (e: HttpStatusCodeException) {
                    if (!e.statusCode.is5xxServerError) throw e
                    String(e.responseBodyAsByteArray)
                } ?: ""
                return jacksonObjectMapper().readValue(responseText, type.java)
            } catch (e: Exception) {
                val errorCode = when (e) {
                    is HttpStatusCodeException -> "ERROR_${e.statusCode}"
                    is RestClientException -> "ERROR_HTTP"
                    is JsonParseException -> "INVALID_JSON"
                    is MismatchedInputException -> "INVALID_JSON"
                    else -> "ERROR_UNKNOWN"
                }
                throw ManagementEndpointException(endpoint, errorCode, url, e)
            }
        }
    }
}