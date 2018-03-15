package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class ManagmentApplicationService(val restTemplate: RestTemplate, val mapper: ObjectMapper) {

    val logger: Logger = LoggerFactory.getLogger(ManagmentApplicationService::class.java)


    fun findManagementEndpoints(podIP: String, managementPath: String): Map<String, String> {
        logger.debug("Find management endpoints ip={}, path={}", podIP, managementPath)
        val managementUrl = "http://${podIP}$managementPath"

        val managementEndpoints = restTemplate.getForObject(managementUrl, JsonNode::class.java)

        if (!managementEndpoints.has("_links")) {
            logger.debug("Management endpoint does not have links at url={}", managementUrl)
            return emptyMap()
        }
        return managementEndpoints["_links"].asMap().mapValues { it.value["href"].asText() }

    }


    fun findResource(url: String?): JsonNode? {
        if (url == null) {
            return null
        }
        logger.debug("Find resource with url={}", url)
        return restTemplate.getForObject(url, JsonNode::class.java)

    }


}