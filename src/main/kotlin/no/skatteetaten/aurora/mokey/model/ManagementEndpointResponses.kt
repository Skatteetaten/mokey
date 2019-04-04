package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.service.DateParser
import java.time.Instant

enum class EndpointType(val key: String) {
    HEALTH("health"),
    INFO("info"),
    ENV("env"),
    DISCOVERY("_links")
}

data class ManagementLinks(private val links: Map<String, String>) {

    fun linkFor(endpointType: EndpointType): String? {
        return links[endpointType.key]
    }

    companion object {
        fun parseManagementResponse(response: JsonNode): ManagementLinks {
            val asMap = response[EndpointType.DISCOVERY.key].asMap()
            val links = asMap
                .mapValues { it.value["href"].asText()!! }
            return ManagementLinks(links)
        }
    }
}

enum class HealthStatus { UP, OBSERVE, COMMENT, UNKNOWN, OUT_OF_SERVICE, DOWN }

data class HealthResponse(
    val status: HealthStatus,
    val parts: Map<String, HealthPart> = emptyMap()
)

data class HealthPart(
    val status: HealthStatus = HealthStatus.UP,
    val details: Map<String, JsonNode> = emptyMap()
)

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
            commitId = value.extract("/commit.id.abbrev", "/commit/id", "/commit.id.short")?.textValue()
            commitTime = extractInstant(value, "/commit/time", "/commit.time/v1", "/commit.time")
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