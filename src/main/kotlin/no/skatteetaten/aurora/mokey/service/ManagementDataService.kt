package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.fabric8.kubernetes.api.model.Pod
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

// Does this need to be an async cache?
val cache: Cache<ManagementCacheKey, ManagementEndpointResult<*>> = Caffeine.newBuilder()
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .maximumSize(100000)
    .build()

// TODO: We have to make sure that replication is correct here when we go to replicaset
fun ManagementEndpoint.toCacheKey() =
    ManagementCacheKey(this.pod.metadata.namespace, this.pod.metadata.name.substringBeforeLast("-"), this.endpointType)

data class ManagementCacheKey(val namespace: String, val replicationName: String, val type: EndpointType)

fun <T> ManagementEndpoint.getCachedOrCompute(
    fn: (endpoint: ManagementEndpoint) -> ManagementEndpointResult<T>
): ManagementEndpointResult<T> {
    val key = this.toCacheKey()
    val cachedResponse = (cache.getIfPresent(key) as ManagementEndpointResult<T>?)?.also {
        logger.debug("Found cached response for $key")
    }
    return cachedResponse ?: fn(this).also {
        logger.debug("Cached management interface $key")
        cache.put(key, it)
    }
}

@Service
class ManagementDataService(
    val client: OpenShiftManagementClient
) {

    fun load(pod: Pod, endpointPath: String?): ManagementData {

        // TODO: validate this
        val (port, path) = try {
            assert(endpointPath != null && endpointPath.isNotBlank()) {
                "Management path is missing"
            }
            val port = endpointPath!!.substringBefore("/").removePrefix(":").toInt()
            val p = endpointPath.substringAfter("/")
            port to p
        } catch (e: Exception) {
            return ManagementData(
                ManagementEndpointResult(
                    errorMessage = e.message,
                    endpointType = EndpointType.DISCOVERY,
                    resultCode = "ERROR_CONFIGURATION"
                )
            )
        }

        val discoveryEndpoint = ManagementEndpoint(pod, port, path, EndpointType.DISCOVERY)

        val discoveryResponse: ManagementEndpointResult<DiscoveryResponse> = discoveryEndpoint.getCachedOrCompute {
            it.findJsonResource(client, DiscoveryResponse::class.java)
        }

        val discoveryResult = discoveryResponse.deserialized ?: return ManagementData((discoveryResponse))

        val info = discoveryResult.createEndpoint(pod, port, EndpointType.INFO)?.let {
            it.getCachedOrCompute { endpoint ->
                endpoint.findJsonResource(client, InfoResponse::class.java)
            }
        } ?: EndpointType.INFO.missingResult()

        val env = discoveryResult.createEndpoint(pod, port, EndpointType.ENV)?.let {
            it.getCachedOrCompute { endpoint ->
                endpoint.findJsonResource(client, JsonNode::class.java)
            }
        } ?: EndpointType.ENV.missingResult()

        val health = discoveryResult.createEndpoint(pod, port, EndpointType.HEALTH)?.let {
            parseHealthResult(it.findJsonResource(client, JsonNode::class.java))
        } ?: EndpointType.HEALTH.missingResult()

        return ManagementData(
            links = discoveryResponse,
            info = info,
            env = env,
            health = health
        )
    }

    private fun parseHealthResult(it: ManagementEndpointResult<JsonNode>): ManagementEndpointResult<JsonNode> {
        if (!it.isSuccess) {
            return it
        }
        val statusField = it.deserialized?.at("/status")
        if (statusField == null || statusField is MissingNode) {
            return ManagementEndpointResult(
                errorMessage = "Invalid format, does not contain status",
                endpointType = EndpointType.HEALTH,
                resultCode = "INVALID_FORMAT"
            )
        }

        try {
            HealthStatus.valueOf(statusField.textValue())
        } catch (e: Exception) {
            return ManagementEndpointResult(
                errorMessage = "Invalid format, status is not valid HealthStatus value",
                endpointType = EndpointType.HEALTH,
                resultCode = "INVALID_FORMAT"
            )
        }
        return it
    }
}

fun <T : Any> EndpointType.missingResult(): ManagementEndpointResult<T> {
    return ManagementEndpointResult(
        errorMessage = "Unknown endpoint link",
        endpointType = this,
        resultCode = "LINK_MISSING"
    )
}
