package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.service.DateParser
import java.time.Instant

enum class EndpointType(val key: String) {
    HEALTH("health"),
    INFO("info"),
    ENV("env"),
    DISCOVERY("_links")
}

data class ManagementEndpoint(val pod: Pod, val port: Int, val path: String, val endpointType: EndpointType) {
    // val url = "namespaces/${pod.metadata.namespace}/pods/${pod.metadata.name}:$port/proxy/$path"
    val url = "http://${pod.status.podIP}:$port/$path"
}

// TODO: We have to make sure that replication is correct here when we go to replicaset
fun ManagementEndpoint.toCacheKey(): ManagementCacheKey {
    val name = if (endpointType == EndpointType.INFO) {
        this.pod.metadata.name
    } else {
        this.pod.metadata.name.substringBeforeLast("-")
    }

    return ManagementCacheKey(this.pod.metadata.namespace, name, this.endpointType)
}

data class ManagementCacheKey(val namespace: String, val name: String, val type: EndpointType)

enum class HealthStatus { UP, OBSERVE, COMMENT, UNKNOWN, OUT_OF_SERVICE, DOWN }

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscoveryResponse(
    val _links: Map<String, DiscoveryLink>
)

fun DiscoveryResponse.createEndpoint(pod: Pod, port: Int, type: EndpointType): ManagementEndpoint? {
    return this._links[type.key.toLowerCase()]?.path?.let {
        ManagementEndpoint(pod, port, it, type)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class DiscoveryLink(
    val href: String
) {

    val path: String get() = href.replace("http://", "").substringAfter("/")
}

fun <T : Any> EndpointType.missingResult(): ManagementEndpointResult<T> {
    return ManagementEndpointResult(
        errorMessage = "Unknown endpoint link",
        endpointType = this,
        resultCode = "LINK_MISSING"
    )
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
