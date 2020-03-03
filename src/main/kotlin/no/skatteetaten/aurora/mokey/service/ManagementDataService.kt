package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.fabric8.kubernetes.api.model.Pod
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

// Does this need to be an async cache?
val cache: Cache<ManagementCacheKey, ManagementEndpointResult<*>> = Caffeine.newBuilder()
    .expireAfterAccess(10, TimeUnit.MINUTES)
    .maximumSize(10000)
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
        val p = ManagementInterface.create(client, pod, endpointPath)

        if (p.first == null) {
            return ManagementData(links = p.second)
        }

        val mgmtInterface = p.first!!

        val info = mgmtInterface.infoEndpoint?.let {
            it.getCachedOrCompute { endpoint ->
                endpoint.findJsonResource(client, InfoResponse::class.java)
            }
        } ?: EndpointType.INFO.missingResult()

        val env = mgmtInterface.envEndpoint?.let {
            it.getCachedOrCompute { endpoint ->
                endpoint.findJsonResource(client, JsonNode::class.java)
            }
        } ?: EndpointType.ENV.missingResult()

        val health = mgmtInterface.healthEndpoint?.let {
            it.findJsonResource(client, HealthResponseParser::parse)
        } ?: EndpointType.HEALTH.missingResult()

        return ManagementData(
            links = p.second,
            info = info,
            env = env,
            health = health
        )
    }
}

fun <T : Any> EndpointType.missingResult(): ManagementEndpointResult<T> {
    return ManagementEndpointResult(
        errorMessage = "Unknown endpoint link",
        endpointType = this,
        resultCode = "LINK_MISSING"
    )
}
