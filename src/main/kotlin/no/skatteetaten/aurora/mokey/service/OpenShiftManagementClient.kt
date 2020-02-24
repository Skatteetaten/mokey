package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

@Service
class OpenShiftManagementClient(
    @Qualifier("managmenetClient") val client: KubernetesCoroutinesClient
) {
    suspend fun proxyManagementInterfaceRaw(pod: Pod, port: Int, path: String): String =
        client.proxyGet(pod, port, path)
}