package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.service.Endpoint.*
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

class ManagementEndpointException(val endpoint: Endpoint, val errorCode: String, cause: Exception? = null)
    : RuntimeException("${endpoint}_$errorCode", cause)

enum class Endpoint(val key: String) { HEALTH("health"), INFO("info"), ENV("env"), MANAGEMENT("_links") }

data class ManagementLinks(private val links: Map<String, String>) {

    fun linkFor(endpoint: Endpoint): String {
        return links[endpoint.key] ?: throw ManagementEndpointException(endpoint, "LINK_MISSING")
    }

    companion object {
        fun parseManagementResponse(response: JsonNode): ManagementLinks {
            return try {
                val asMap = response[MANAGEMENT.key].asMap()
                val links = asMap
                        .mapValues { it.value["href"].asText()!! }
                ManagementLinks(links)
            } catch (e: Exception) {
                throw ManagementEndpointException(MANAGEMENT, "INVALID_FORMAT", e)
            }
        }
    }
}

enum class HealthStatus { UP, OBSERVE, COMMENT, UNKNOWN, OUT_OF_SERVICE, DOWN }

data class HealthResponse(
        val status: HealthStatus,
        val parts: MutableMap<String, HealthPart> = mutableMapOf()
) {
    @JsonAnySetter(enabled = true)
    fun setAny(name: String, value: HealthPart) {
        parts[name] = value
    }
}

data class HealthPart(val status: HealthStatus, val details: MutableMap<String, JsonNode> = mutableMapOf()) {
    @JsonAnySetter(enabled = true)
    fun setAny(name: String, value: JsonNode) {
        details[name] = value
    }
}

class ManagementEndpoint internal constructor(
        private val restTemplate: RestTemplate,
        val links: ManagementLinks
) {

    @Throws(ManagementEndpointException::class)
    fun getHealthEndpointResponse(): HealthResponse = findJsonResource(HEALTH, HealthResponse::class)

    @Throws(ManagementEndpointException::class)
    fun getInfoEndpointResponse(): JsonNode = findJsonResource(INFO, JsonNode::class)

    @Throws(ManagementEndpointException::class)
    fun getEnvEndpointResponse(): JsonNode = findJsonResource(ENV, JsonNode::class)

    private fun <T : Any> findJsonResource(endpoint: Endpoint, type: KClass<T>): T {
        return findJsonResource(restTemplate, endpoint, links.linkFor(endpoint), type)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

        @Throws(ManagementEndpointException::class)
        fun create(restTemplate: RestTemplate, managementUrl: String): ManagementEndpoint {

            val response = findJsonResource(restTemplate, Endpoint.MANAGEMENT, managementUrl, JsonNode::class)
            val links = ManagementLinks.parseManagementResponse(response)
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
                    else -> "ERROR_UNKNOWN"
                }
                throw ManagementEndpointException(endpoint, errorCode, e)
            }
        }
    }
}