package no.skatteetaten.aurora.mokey.service

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

@Service
class ManagementEndpointFactory(val restTemplate: RestTemplate) {
    fun create(managementUrl: String): ManagementEndpoint {
        return ManagementEndpoint.create(restTemplate, managementUrl)
    }
}

class ManagementEndpointException(val endpoint: Endpoint, val errorCode: String, cause: Exception? = null)
    : RuntimeException("${endpoint}_$errorCode", cause)

enum class Endpoint(val key: String) { HEALTH("health"), INFO("info"), MANAGEMENT("_links") }

data class ManagementLinks(private val links: Map<String, String>) {

    fun linkFor(endpoint: Endpoint): String {
        return links[endpoint.key] ?: throw ManagementEndpointException(endpoint, "LINK_MISSING")
    }

    companion object {
        fun parseManagementResponse(response: JsonNode): ManagementLinks {
            return try {
                val links = response[MANAGEMENT.key].asMap()
                        .mapValues { it.value["href"].asText()!! }
                ManagementLinks(links)
            } catch (e: Exception) {
                throw ManagementEndpointException(MANAGEMENT, "INVALID_FORMAT", e)
            }
        }
    }
}

class ManagementEndpoint private constructor(
        private val restTemplate: RestTemplate,
        val links: ManagementLinks
) {

    @Throws(ManagementEndpointException::class)
    fun getHealthEndpointResponse(): JsonNode {
        return findJsonResource(HEALTH)
    }

    @Throws(ManagementEndpointException::class)
    fun getInfoEndpointResponse(): JsonNode {
        return findJsonResource(INFO)
    }

    private fun findJsonResource(endpoint: Endpoint): JsonNode {
        return findJsonResource(restTemplate, endpoint, links.linkFor(endpoint))
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

        fun create(restTemplate: RestTemplate, managementUrl: String): ManagementEndpoint {

            val response = findJsonResource(restTemplate, Endpoint.MANAGEMENT, managementUrl)
            val links = ManagementLinks.parseManagementResponse(response)
            return ManagementEndpoint(restTemplate, links)
        }

        private fun findJsonResource(restTemplate: RestTemplate, endpoint: Endpoint, url: String): JsonNode {

            logger.debug("Getting resource with url={}", url)
            try {
                return restTemplate.getForObject<JsonNode>(url, JsonNode::class.java)!!
            } catch (e: Exception) {
                var cause = e
                val errorCode = when (e) {
                    is HttpStatusCodeException -> {
                        if (e.statusCode.is5xxServerError) {
                            try {
                                return jacksonObjectMapper().readTree(e.responseBodyAsByteArray)
                            } catch (e: Exception) {
                                cause = e
                                "INVALID_JSON"
                            }
                        } else {
                            "ERROR_${e.statusCode}"
                        }
                    }
                    is RestClientException -> "ERROR_HTTP"
                    else -> "ERROR_UNKNOWN"
                }
                throw ManagementEndpointException(endpoint, errorCode, cause)
            }
        }
    }
}