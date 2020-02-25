package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import kotlinx.coroutines.reactive.awaitFirst
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Duration

@Service
class OpenShiftManagementClient(
    @Qualifier("managmenetClient") val client: KubernetesReactorClient
) {
    suspend fun proxyManagementInterfaceRaw(pod: Pod, port: Int, path: String): String {
        return client.proxyGet<String>(pod, port, path).timeout(
            Duration.ofSeconds(2),
            Mono.error<String>(RuntimeException("Timed out getting management interface in namespace=${pod.metadata.namespace}  pod=${pod.metadata.name} path=$path for "))
        ).awaitFirst()
    }
}
