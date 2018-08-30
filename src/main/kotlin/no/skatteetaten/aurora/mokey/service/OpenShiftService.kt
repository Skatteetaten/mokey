package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import org.springframework.retry.annotation.Backoff
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentList
import okhttp3.Request
import org.springframework.retry.annotation.Retryable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
@Retryable(value = [(KubernetesClientException::class)], maxAttempts = 3, backoff = Backoff(delay = 500))
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    fun dc(namespace: String, name: String): DeploymentConfig? {
        return openShiftClient.deploymentConfigs().inNamespace(namespace).withName(name).getOrNull()
    }

    fun route(namespace: String, name: String): Route? {
        return openShiftClient.routes().inNamespace(namespace).withName(name).getOrNull()
    }

    fun routes(namespace: String, labelMap: Map<String, String>): List<Route> {
        return openShiftClient.routes().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun services(namespace: String, labelMap: Map<String, String>): List<io.fabric8.kubernetes.api.model.Service> {
        return openShiftClient.services().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> {
        return openShiftClient.pods().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun rc(namespace: String, name: String): ReplicationController? {
        return openShiftClient.replicationControllers().inNamespace(namespace).withName(name).getOrNull()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): ImageStreamTag? {
        return openShiftClient.imageStreamTags().inNamespace(namespace).withName("$name:$tag").getOrNull()
    }

    fun applicationDeployments(namespace: String): List<ApplicationDeployment> {
        return (openShiftClient as DefaultOpenShiftClient).applicationDeployments(namespace)
    }

    fun projects(): List<Project> {
        return openShiftClient.projects().list().items
    }

    // TODO; Denne nå vi ikke glemme å få på plass igjen i mokey eller i gobo
    fun currentUserHasAccess(namespace: String): Boolean {
        val user = SecurityContextHolder.getContext().authentication.principal as User
        val userClient = DefaultOpenShiftClient(ConfigBuilder().withOauthToken(user.token).build())
        return userClient.projects().withName(namespace).getOrNull()?.let { true } ?: false
    }
}

fun DefaultOpenShiftClient.applicationDeployments(namespace: String): List<ApplicationDeployment> {
    // TODO: permissions to get AAI
    val url =
        this.openshiftUrl.toURI().resolve("/apis/skatteetaten.no/v1/namespaces/$namespace/applicationdeployments")
    return try {
        val request = Request.Builder().url(url.toString()).build()
        val response = this.httpClient.newCall(request).execute()
        jacksonObjectMapper().readValue(response.body()?.bytes(), ApplicationDeploymentList::class.java)?.let {
            it.items
        } ?: throw KubernetesClientException("Error occurred while fetching list of applications namespace=$namespace")
    } catch (e: Exception) {
        throw KubernetesClientException("Error occurred while fetching list of applications namespace=$namespace", e)
    }
}
