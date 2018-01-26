package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.client.OpenShiftClient
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service

@Service
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    @Retryable(value = KubernetesClientException::class, maxAttempts = 3, backoff = Backoff(delay = 500))
    fun deploymentConfigs(namespace: String): List<DeploymentConfig> {
        return openShiftClient.deploymentConfigs().inNamespace(namespace).list().items
    }
    @Retryable(value = KubernetesClientException::class, maxAttempts = 3, backoff = Backoff(delay = 500))
    fun imageStream(namespace: String, name: String): ImageStream? {
        return openShiftClient.imageStreams().inNamespace(namespace).withName(name).getOrNull()
    }

    @Retryable(value = KubernetesClientException::class, maxAttempts = 3, backoff = Backoff(delay = 500))
    fun route(namespace: String, name: String): Route? {
        return openShiftClient.routes().inNamespace(namespace).withName(name).getOrNull()
    }
    @Retryable(value = KubernetesClientException::class, maxAttempts = 3, backoff = Backoff(delay = 500))
    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> {
        return openShiftClient.pods().inNamespace(namespace).withLabels(labelMap).list().items

    }
    @Retryable(value = KubernetesClientException::class, maxAttempts = 3, backoff = Backoff(delay = 500))
    fun rc(namespace: String, name: String): ReplicationController? {
        return openShiftClient.replicationControllers().inNamespace(namespace).withName(name).getOrNull()
    }

    @Retryable(value = Exception::class, maxAttempts = 3, backoff = Backoff(delay = 500))
    fun projects(): List<Project> {
        return openShiftClient.projects().list().items
    }
}