package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.model.ManagementData
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

class ManagementEndpoint(
    private val restTemplate: RestTemplate,
    val links: Map<String, String>,
    val violationRules: MutableSet<String> = mutableSetOf()
) {

    fun getManagementData(): ManagementData {

        val info = getInfoEndpointResponse()
        val health = getHealthEndpointResponse()

        return ManagementData(links, info, health)
    }

    fun getHealthEndpointResponse(): JsonNode? {
        return try {
            findResource(links["health"])
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode.is5xxServerError) {
                try {
                    jacksonObjectMapper().readTree(e.responseBodyAsByteArray)
                } catch (e: Exception) {
                    violationRules.add("MANAGEMENT_HEALTH_ERROR_INVALID_JSON")
                    null
                }
            } else {
                violationRules.add("MANAGEMENT_HEALTH_ERROR_${e.statusCode}")
                null
            }
        } catch (e: RestClientException) {
            violationRules.add("MANAGEMENT_HEALTH_ERROR_HTTP")
            null
        }
    }

    fun getInfoEndpointResponse(): JsonNode? {
        return try {
            findResource(links["info"])
        } catch (e: HttpStatusCodeException) {
            violationRules.add("MANAGEMENT_INFO_ERROR_${e.statusCode}")
            null
        } catch (e: RestClientException) {
            violationRules.add("MANAGEMENT_INFO_ERROR_HTTP")
            null
        }
    }

    private fun findResource(url: String?): JsonNode? {
        if (url == null) {
            return null
        }
        logger.debug("Find resource with url={}", url)
        return restTemplate.getForObject(url, JsonNode::class.java)
    }

    companion object {
        val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

        fun create(restTemplate: RestTemplate, managementUrl: String): ManagementEndpoint {

            val violationRules: MutableSet<String> = mutableSetOf()
            val links = getManagementLinks(restTemplate, managementUrl, violationRules)
            return ManagementEndpoint(restTemplate, links, violationRules)
        }

        private fun getManagementLinks(restTemplate: RestTemplate, managementUrl: String, violationRules: MutableSet<String>): Map<String, String> {
            val links = try {
                val managementEndpoints = restTemplate.getForObject(managementUrl, JsonNode::class.java)

                if (!managementEndpoints.has("_links")) {
                    logger.debug("Management endpoint does not have links at url={}", managementUrl)
                    emptyMap()
                } else {
                    managementEndpoints["_links"].asMap()
                        .mapValues { it.value["href"].asText() }
                        .also {
                            if (it.isEmpty()) {
                                violationRules.add("MANAGEMENT_ENDPOINT_NOT_VALID_FORMAT")
                            }
                        }
                }
            } catch (e: HttpStatusCodeException) {
                violationRules.add("MANAGEMENT_ENDPOINT_ERROR_${e.statusCode}")
                null
            } catch (e: Exception) {
                violationRules.add("MANAGEMENT_ENDPOINT_ERROR_HTTP")
                null
            }
            return links ?: emptyMap()
        }

        private fun findManagementLinks(restTemplate: RestTemplate, managementUrl: String): Map<String, String> {
            val managementEndpoints = restTemplate.getForObject(managementUrl, JsonNode::class.java)

            if (!managementEndpoints.has("_links")) {
                logger.debug("Management endpoint does not have links at url={}", managementUrl)
                return emptyMap()
            }
            return managementEndpoints["_links"].asMap().mapValues { it.value["href"].asText() }
        }
    }
}