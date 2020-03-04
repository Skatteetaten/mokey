package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.fabric8.kubernetes.api.model.Pod
import kotlinx.coroutines.reactive.awaitFirst
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.mokey.model.EndpointType
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class OpenShiftManagementClient(
    @Qualifier("managmenetClient") val client: KubernetesReactorClient
) {
    suspend fun proxyManagementInterfaceRaw(pod: Pod, port: Int, path: String): String {
        return client.proxyGet<String>(
            pod = pod,
            port = port,
            path = path,
            headers = mapOf(HttpHeaders.ACCEPT to "application/vnd.spring-boot.actuator.v2+json,application/json")
        )
            .timeout(
                Duration.ofSeconds(2),
                Mono.error<String>(RuntimeException("Timed out getting management interface in namespace=${pod.metadata.namespace}  pod=${pod.metadata.name} path=$path for "))
            ).awaitFirst()
    }

    // TODO: Here we should have methods for each of the different endpoints that return ManagementEndpointResult
}

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