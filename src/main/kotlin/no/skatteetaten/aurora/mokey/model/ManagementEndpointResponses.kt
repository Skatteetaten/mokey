package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.service.DateParser
import no.skatteetaten.aurora.mokey.service.ManagementEndpointException
import java.time.Instant

enum class Endpoint(val key: String) {
    HEALTH("health"),
    INFO("info"),
    ENV("env"),
    MANAGEMENT("_links")
}

data class ManagementLinks(private val links: Map<String, String>) {

    fun linkFor(endpoint: Endpoint): String {
        return links[endpoint.key] ?: throw ManagementEndpointException(endpoint, "LINK_MISSING")
    }

    companion object {
        fun parseManagementResponse(response: JsonNode): ManagementLinks {
            val asMap = response[Endpoint.MANAGEMENT.key].asMap()
            val links = asMap
                    .mapValues { it.value["href"].asText()!! }
            return ManagementLinks(links)
        }
    }
}

data class HttpResponse<T>(
    val deserialized: T,
    val textResponse: String,
    val createdAt: Instant = Instant.now()
)

enum class HealthStatus { UP, OBSERVE, COMMENT, UNKNOWN, OUT_OF_SERVICE, DOWN }

data class HealthResponse(
    val status: HealthStatus,
    val details: MutableMap<String, HealthPart> = mutableMapOf()
) {
    @JsonAnySetter(enabled = true)
    private fun setAny(name: String, value: HealthPart) {
        details[name] = value
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
            buildTime = buildTime ?: extractInstant(value, "/build.time")
        }
        if (name == "build") {
            buildTime = buildTime ?: extractInstant(value, "/time")
        }
    }

    private fun extractInstant(value: JsonNode, vararg pathAlternatives: String): Instant? {
        val timeString: String? = value.extract(*pathAlternatives)?.textValue()
        return timeString?.let { DateParser.parseString(it) }
    }
}