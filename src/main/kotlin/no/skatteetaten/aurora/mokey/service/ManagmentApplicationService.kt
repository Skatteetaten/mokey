package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class ManagmentApplicationService(val restTemplate: RestTemplate, val mapper: ObjectMapper) {

    val logger: Logger = LoggerFactory.getLogger(ManagmentApplicationService::class.java)


    fun findManagementEndpoints(podIP: String, managementPath: String): Map<String, String> {

        logger.debug("Find management endpoints ip={}, path={}", podIP, managementPath)
        val managementUrl = "http://${podIP}$managementPath"

        val managementEndpoints = try {
            restTemplate.getForObject(managementUrl, JsonNode::class.java)
        } catch (e: HttpStatusCodeException) {
            mapper.readTree(e.responseBodyAsByteArray).also{
                logger.warn("Error getting management endpoint url=$managementUrl code=${e.statusCode} body=${mapper.writerWithDefaultPrettyPrinter().writeValueAsString(it)}", e)
            }
            return emptyMap()
        } catch (e: RestClientException) {
            logger.warn("Error getting management endpoints url=$managementUrl", e)
            return emptyMap()
        }

        if (!managementEndpoints.has("_links")) {
            logger.debug("Management endpoint does not have links at url={}", managementUrl)
            return emptyMap()
        }
        return managementEndpoints["_links"].asMap().mapValues { it.value["href"].asText() }

    }


    fun findResource(url: String?, namespace: String, name: String): JsonNode? {
        if (url == null) {
            return null
        }
        return try {
            logger.debug("Find resource with url={}", url)
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: HttpStatusCodeException) {
            return mapper.readTree(e.responseBodyAsByteArray)
        } catch (e: RestClientException) {
            logger.warn("Error getting resource for namespace={} name={} url={}", namespace, name, url)
            null
        }
    }


}