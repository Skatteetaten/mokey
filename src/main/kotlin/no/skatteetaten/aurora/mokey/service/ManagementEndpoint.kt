package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.Endpoint.ENV
import no.skatteetaten.aurora.mokey.model.Endpoint.HEALTH
import no.skatteetaten.aurora.mokey.model.Endpoint.INFO
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.HttpResponse
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

class ManagementEndpointException(
    val endpoint: Endpoint,
    val errorCode: String,
    url: String? = null,
    cause: Exception? = null
) : RuntimeException("${endpoint}_$errorCode", cause)

class ManagementEndpoint internal constructor(
    private val restTemplate: RestTemplate,
    val links: ManagementLinks
) {

    @Throws(ManagementEndpointException::class)
    fun getHealthEndpointResponse(): HttpResponse<HealthResponse> {
        val response = findJsonResource(HEALTH, JsonNode::class)
        val healthResponse = HealthResponseParser.parse(response.deserialized)
        return HttpResponse(healthResponse, response.textResponse, response.createdAt)
    }

    @Throws(ManagementEndpointException::class)
    fun getInfoEndpointResponse(): HttpResponse<InfoResponse> = findJsonResource(INFO, InfoResponse::class)

    @Throws(ManagementEndpointException::class)
    fun getEnvEndpointResponse(): HttpResponse<JsonNode> = findJsonResource(ENV, JsonNode::class)

    private fun <T : Any> findJsonResource(endpoint: Endpoint, type: KClass<T>) =
        findJsonResource(restTemplate, endpoint, links.linkFor(endpoint), type)

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

        @Throws(ManagementEndpointException::class)
        fun create(restTemplate: RestTemplate, managementUrl: String): ManagementEndpoint {

            val response = findJsonResource(restTemplate, Endpoint.MANAGEMENT, managementUrl, JsonNode::class)
            val links = try {
                ManagementLinks.parseManagementResponse(response.deserialized)
            } catch (e: Exception) {
                throw ManagementEndpointException(Endpoint.MANAGEMENT, "INVALID_FORMAT", managementUrl, e)
            }

            return ManagementEndpoint(restTemplate, links)
        }

        private fun <T : Any> findJsonResource(
            restTemplate: RestTemplate,
            endpoint: Endpoint,
            url: String,
            type: KClass<T>
        ): HttpResponse<T> {

            logger.debug("Getting resource with url={}", url)
            try {
                val responseText: String = try {
                    restTemplate.getForObject(url, String::class.java)
                } catch (e: HttpStatusCodeException) {
                    if (!e.statusCode.is5xxServerError) throw e
                    String(e.responseBodyAsByteArray)
                } ?: ""
                return toHttpResponse(responseText, type.java)
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

        @JvmStatic
        fun <T : Any> toHttpResponse(jsonString: String, clazz: Class<T>): HttpResponse<T> {
            val deserialized = jacksonObjectMapper().readValue(jsonString, clazz)
            return HttpResponse(deserialized, jsonString)
        }
    }
}

object HealthResponseParser {

    enum class HealthResponseFormat { SPRING_BOOT_1X, SPRING_BOOT_2X }

    private const val STATUS_PROPERTY = "status"
    private const val DETAILS_PROPERTY = "details"

    fun parse(json: JsonNode): HealthResponse = when (json.format) {
        HealthResponseFormat.SPRING_BOOT_2X -> handleSpringBoot2Format(json)
        else -> handleSpringBoot1Format(json)
    }

    private fun handleSpringBoot2Format(json: JsonNode): HealthResponse {
        val healthStatus = json.status
        val allDetails = json.details
        val parts = allDetails.mapValues {
            val status = it.value.status
            val partDetails = it.value.details
            HealthPart(status, partDetails)
        }
        return HealthResponse(healthStatus, parts)
    }

    private fun handleSpringBoot1Format(json: JsonNode): HealthResponse {
        val healthStatus = json.status
        val allDetails = json.allNodesExceptStatus
        val parts = allDetails.mapValues {
            val status = it.value.status
            val partDetails = it.value.allNodesExceptStatus
            HealthPart(status, partDetails)
        }
        return HealthResponse(healthStatus, parts)
    }

    private val JsonNode.format
        get(): HealthResponseFormat {
            return if (this.has(DETAILS_PROPERTY) && this.size() == 2) {
                HealthResponseFormat.SPRING_BOOT_2X
            } else {
                HealthResponseFormat.SPRING_BOOT_1X
            }
        }

    private val JsonNode.details get() = this.extract("/$DETAILS_PROPERTY").asMap()

    private val JsonNode.allNodesExceptStatus
        get() =
            this.asMap().toMutableMap().also { it.remove(STATUS_PROPERTY) }.toMap()

    private val JsonNode.status
        get(): HealthStatus {
            return try {
                this.extract("/$STATUS_PROPERTY")?.textValue()?.let { HealthStatus.valueOf(it) }
            } catch (e: Throwable) {
                null
            } ?: throw IllegalArgumentException("Element did not contain valid $STATUS_PROPERTY property")
        }
}