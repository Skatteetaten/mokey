package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.service.Endpoint.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
    private fun setAny(name: String, value: HealthPart) {
        parts[name] = value
    }
}

data class HealthPart(val status: HealthStatus, val details: MutableMap<String, JsonNode> = mutableMapOf()) {
    @JsonAnySetter(enabled = true)
    private fun setAny(name: String, value: JsonNode) {
        details[name] = value
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class InfoResponse(
        val podLinks: Map<String, String> = mapOf(),
        val serviceLinks: Map<String, String> = mapOf(),
        val dependencies: Map<String, String> = mapOf(),
        var commitId: String? = null,
        var commitTime: Instant? = null,
        var buildTime: Instant? = null
) {

    @JsonAnySetter(enabled = true)
    private fun setAny(name: String, value: JsonNode) {
        if (name == "git") {
            commitId = value.extract("/commit.id.abbrev", "/commit/id")?.textValue()
            commitTime = extractInstant(value, "/commit.time", "/commit/time")
        }
        if (name == "build") {
            buildTime = extractInstant(value, "/time")
        }
    }

    private fun extractInstant(value: JsonNode, vararg pathAlternatives: String): Instant? {
        val timeString: String? = value.extract(*pathAlternatives)?.textValue()
        return timeString?.let { DateParser.parseString(it) }
    }
}

object DateParser {
    val formatters = listOf(
            DateTimeFormatter.ofPattern("dd.MM.yyyy '@' HH:mm:ss z"), // Ex: 26.03.2018 @ 13:31:39 CEST
            DateTimeFormatter.ISO_DATE_TIME // Ex: 2018-03-23T10:53:31Z
    )

    fun parseString(dateString: String): Instant? {
        formatters.forEach {
            try {
                return it.parse(dateString, Instant::from)
            } catch (e: DateTimeParseException) {
            }
        }
        return null
    }
}

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
                    is MismatchedInputException -> "INVALID_JSON"
                    else -> "ERROR_UNKNOWN"
                }
                throw ManagementEndpointException(endpoint, errorCode, e)
            }
        }
    }
}