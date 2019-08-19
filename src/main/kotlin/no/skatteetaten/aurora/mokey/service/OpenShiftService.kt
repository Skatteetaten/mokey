package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.api.model.RouteList
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentList
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewResourceAttributes
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewSpec
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono

@Service
@Retryable(value = [(KubernetesClientException::class)], maxAttempts = 3, backoff = Backoff(delay = 500))
class OpenShiftService(val openShiftClient: OpenShiftClient, val webClient: WebClient) {

    val logger: Logger = LoggerFactory.getLogger(OpenShiftService::class.java)
    fun dc(namespace: String, name: String): DeploymentConfig? {
        return openShiftClient.deploymentConfigs().inNamespace(namespace).withName(name).getOrNull()
    }

    fun dcWebClient(namespace: String, name: String) = try {
        webClient
            .get()
            .uri("/apis/apps.openshift.io/v1/namespaces/$namespace/deploymentconfigs/$name")
            .retrieve()
            .bodyToMono<DeploymentConfig>()
            .block()
    } catch (e: Throwable) {
        null
    }

    // https://utv-master.paas.skead.no:8443/apis/route.openshift.io/v1/namespaces/aurora/routes/rosita-ui-webseal
    fun route(namespace: String, name: String): Route? {
        return openShiftClient.routes().inNamespace(namespace).withName(name).getOrNull()
    }

    fun routeWebClient(namespace: String, name: String) = try {
        webClient
            .get()
            .uri("/apis/apps.openshift.io/v1/namespaces/$namespace/routes/$name")
            .retrieve()
            .bodyToMono<Route>()
            .block()
    } catch (e: Throwable) {
        null
    }

    // https://utv-master.paas.skead.no:8443/apis/route.openshift.io/v1/namespaces/aurora/routes?labelSelector=app%3Dargus
    // https://utv-master.paas.skead.no:8443/apis/route.openshift.io/v1/namespaces/aurora/routes
    fun routes(namespace: String, labelMap: Map<String, String>): List<Route> {
        return openShiftClient.routes().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun routesWebClient(namespace: String, labelMap: Map<String, String>) = webClient
        .get()
        .uri {
            val selectors = labelMap
                .map { entry -> "${entry.key}=${entry.value}" }
                .joinToString(",")
            it.path("/apis/route.openshift.io/v1/namespaces/$namespace/routes").queryParam("labelSelector", selectors)
                .build()
        }
        .retrieve()
        .bodyToMono<RouteList>()
        .block()?.items ?: emptyList()

    // https://utv-master.paas.skead.no:8443/api/v1/namespaces/aurora/services
// https://utv-master.paas.skead.no:8443/api/v1/namespaces/aurora/services?labelSelector=app%3Dconsole
    fun services(namespace: String, labelMap: Map<String, String>): List<io.fabric8.kubernetes.api.model.Service> {
        return openShiftClient.services().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun servicesWebClient(namespace: String, labelMap: Map<String, String>) = webClient
        .get()
        .uri {
            val selectors = labelMap
                .map { entry -> "${entry.key}=${entry.value}" }
                .joinToString(",")
            it.path("/api/v1/namespaces/$namespace/services").queryParam("labelSelector", selectors).build()
        }
        .retrieve()
        .bodyToMono<ServiceList>()
        .block()?.items ?: emptyList()

    // https://utv-master.paas.skead.no:8443/api/v1/namespaces/aurora-mogen-jenkins/pods?labelSelector=application%3Djenkins-slave%2Cdeploymentconfig%3Djenkins-slave
    // https://utv-master.paas.skead.no:8443/api/v1/namespaces/aurora/pods?labelSelector=name%3Dcantus
    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> {
        return openShiftClient.pods().inNamespace(namespace).withLabels(labelMap).list().items
    }

    fun podsWebClient(namespace: String, labelMap: Map<String, String>) = try {
        webClient
            .get()
            .uri {
                val selectors = labelMap
                    .map { entry -> "${entry.key}=${entry.value}" }
                    .joinToString(",")
                it.path("/api/v1/namespaces/$namespace/pods").queryParam("labelSelector", selectors).build()
            }
            .retrieve()
            .bodyToMono<PodList>()
            .block()?.items ?: emptyList()
    } catch (e: Throwable) {
        emptyList<Pod>()
    }

    // https://utv-master.paas.skead.no:8443/api/v1/namespaces/aurora/replicationcontrollers/cantus-55
    fun rc(namespace: String, name: String): ReplicationController? {
        return openShiftClient.replicationControllers().inNamespace(namespace).withName(name).getOrNull()
    }

    fun rcWebClient(namespace: String, name: String) = try {
        webClient
            .get()
            .uri("/apis/apps.openshift.io/v1/namespaces/$namespace/replicationcontrollers/$name")
            .retrieve()
            .bodyToMono<ReplicationController>()
            .block()
    } catch (e: Throwable) {
        null
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): ImageStreamTag? {
        return openShiftClient.imageStreamTags().inNamespace(namespace).withName("$name:$tag").getOrNull()
    }

    // https://utv-master.paas.skead.no:8443/apis/image.openshift.io/v1/namespaces/aurora/imagestreamtags/console:default
    fun imageStreamTagWebClient(namespace: String, name: String, tag: String) = try {
        webClient
            .get()
            .uri("/apis/apps.openshift.io/v1/namespaces/$namespace/imagestreamtags/$name:$tag")
            .retrieve()
            .bodyToMono<ImageStreamTag>()
            .block()
    } catch (e: Throwable) {
        null
    }

    fun applicationDeployments(namespace: String): List<ApplicationDeployment> {
        return (openShiftClient as DefaultOpenShiftClient).applicationDeployments(namespace)
    }

    fun applicationDeployment(namespace: String, name: String): ApplicationDeployment {
        return (openShiftClient as DefaultOpenShiftClient).applicationDeployment(namespace, name)
    }

    fun projects(): List<Project> = openShiftClient.projects().list().items

    fun projectByNamespaceForUser(namespace: String): Project? =
        createUserClient().projects().withName(namespace).getOrNull()

    fun projectsForUser(): Set<Project> = createUserClient().projects().list().items.toSet()

    fun canViewAndAdmin(namespace: String): Boolean {

        val review = SelfSubjectAccessReview(
            spec = SelfSubjectAccessReviewSpec(
                resourceAttributes = SelfSubjectAccessReviewResourceAttributes(
                    namespace = namespace,
                    verb = "update",
                    resource = "deploymentconfigs"
                )
            )
        )
        val result = createUserClient().selfSubjectAccessView(review)
        return result.status.allowed
    }

    private fun createUserClient(): DefaultOpenShiftClient {
        val user = SecurityContextHolder.getContext().authentication.principal as User
        return DefaultOpenShiftClient(ConfigBuilder().withOauthToken(user.token).build())
    }
}

private val logger = LoggerFactory.getLogger(OpenShiftService::class.java)

fun DefaultOpenShiftClient.selfSubjectAccessView(review: SelfSubjectAccessReview): SelfSubjectAccessReview {

    val url = this.openshiftUrl.toURI().resolve("/apis/authorization.k8s.io/v1/selfsubjectaccessreviews")
    return try {
        val request = Request.Builder()
            .url(url.toString())
            .post(
                RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    jacksonObjectMapper().writeValueAsString(review)
                )
            )
            .build()
        val response = this.httpClient.newCall(request).execute()
        jacksonObjectMapper().readValue(response.body?.bytes(), SelfSubjectAccessReview::class.java)
            ?: throw KubernetesClientException("Error occurred while SelfSubjectAccessReview")
    } catch (e: Exception) {
        throw KubernetesClientException("Error occurred while posting SelfSubjectAccessReview", e)
    }
}

fun DefaultOpenShiftClient.applicationDeployment(namespace: String, name: String): ApplicationDeployment {
    val url =
        this.openshiftUrl.toURI().resolve("/apis/skatteetaten.no/v1/namespaces/$namespace/applicationdeployments/$name")
    logger.debug("Requesting url={}", url)
    return try {
        val request = Request.Builder().url(url.toString()).build()
        val response = this.httpClient.newCall(request).execute()
        jacksonObjectMapper().readValue(response.body?.bytes(), ApplicationDeployment::class.java)
            ?: throw KubernetesClientException("Error occurred while fetching application in namespace=$namespace with name=$name")
    } catch (e: Exception) {
        throw KubernetesClientException(
            "Error occurred while fetching list of applications namespace=$namespace with name=$name",
            e
        )
    }
}

fun DefaultOpenShiftClient.applicationDeployments(namespace: String): List<ApplicationDeployment> {
    val url =
        this.openshiftUrl.toURI().resolve("/apis/skatteetaten.no/v1/namespaces/$namespace/applicationdeployments")
    logger.debug("Requesting url={}", url)
    return try {
        val request = Request.Builder().url(url.toString()).build()
        val response = this.httpClient.newCall(request).execute()
        jacksonObjectMapper().readValue(response.body?.bytes(), ApplicationDeploymentList::class.java)
            ?.items
            ?: throw KubernetesClientException("Error occurred while fetching list of applications in namespace=$namespace")
    } catch (e: Exception) {
        throw KubernetesClientException("Error occurred while fetching list of applications namespace=$namespace", e)
    }
}
