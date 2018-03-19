package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStream
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import okhttp3.Request
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
@Retryable(value = [(KubernetesClientException::class)], maxAttempts = 3, backoff = Backoff(delay = 500))
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    fun deploymentConfigs(namespace: String): List<DeploymentConfig> {
        return openShiftClient.deploymentConfigs().inNamespace(namespace).list().items
    }

    fun imageStream(namespace: String, name: String): ImageStream? {
        return openShiftClient.imageStreams().inNamespace(namespace).withName(name).getOrNull()
    }

    fun route(namespace: String, name: String): Route? {
        return openShiftClient.routes().inNamespace(namespace).withName(name).getOrNull()
    }

    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> {
        return openShiftClient.pods().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun rc(namespace: String, name: String): ReplicationController? {
        return openShiftClient.replicationControllers().inNamespace(namespace).withName(name).getOrNull()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): ImageStreamTag? {
        return (openShiftClient as DefaultOpenShiftClient).customImageStreamTag(namespace, name, tag)
    }

    fun projects(): List<Project> {
        return openShiftClient.projects().list().items
    }

    fun currentUserHasAccess(namespace: String): Boolean {
        val user = SecurityContextHolder.getContext().authentication.principal as User
        val userClient = DefaultOpenShiftClient(ConfigBuilder().withOauthToken(user.token).build())
        return userClient.projects().withName(namespace).getOrNull()?.let { true } ?: false
    }
}

fun DefaultOpenShiftClient.customImageStreamTag(namespace: String, name: String, tag: String): ImageStreamTag? {
    val url = this.openshiftUrl.toURI().resolve("namespaces/$namespace/imagestreamtags/$name:$tag")
    return try {
        val request = Request.Builder().url(url.toString()).build()
        val response = this.httpClient.newCall(request).execute()
        jacksonObjectMapper().readValue(response.body()?.bytes(), ImageStreamTag::class.java)
    } catch (e: Exception) {
        throw KubernetesClientException("error occurred while fetching imageStreamTag" +
            " namespace=$namespace name=$name tag=$tag", e)
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageStreamTag(val image: Image?) {
    val auroraVersion: String
        get() = image?.dockerImageMetadata?.containerConfig?.env
            ?.find { it.contains("AURORA_VERSION") }
            ?.split("=")
            ?.lastOrNull() ?: ""
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Image(val dockerImageMetadata: Metadata)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Metadata(@JsonProperty("ContainerConfig") val containerConfig: ContainerConfig)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContainerConfig(@JsonProperty("Env") val env: List<String>)