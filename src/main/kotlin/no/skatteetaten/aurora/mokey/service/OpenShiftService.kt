package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.client.DefaultOpenShiftClient
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewResourceAttributes
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewSpec
import no.skatteetaten.aurora.openshift.webclient.OpenShiftClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.retry.retryExponentialBackoff
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
@Retryable(value = [(KubernetesClientException::class)], maxAttempts = 3, backoff = Backoff(delay = 500))
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    fun dc(namespace: String, name: String) =
        openShiftClient.deploymentConfig(namespace, name).blockWithRetryAndTimeout()

    fun route(namespace: String, name: String) =
        openShiftClient.route(namespace, name).blockWithRetryAndTimeout()

    fun routes(namespace: String, labelMap: Map<String, String>) =
        openShiftClient.routes(namespace, labelMap).blockWithRetryAndTimeout()?.items ?: emptyList()

    fun services(namespace: String, labelMap: Map<String, String>) =
        openShiftClient.services(namespace, labelMap).blockWithRetryAndTimeout()?.items ?: emptyList()

    fun pods(namespace: String, labelMap: Map<String, String>) =
        openShiftClient.pods(namespace, labelMap).blockWithRetryAndTimeout()?.items ?: emptyList()

    fun rc(namespace: String, name: String) =
        openShiftClient.replicationController(namespace, name).blockWithRetryAndTimeout()

    fun imageStreamTag(namespace: String, name: String, tag: String) =
        openShiftClient.imageStreamTag(namespace, name, tag).blockWithRetryAndTimeout()

    fun applicationDeployments(namespace: String) =
        openShiftClient.applicationDeployments(namespace).blockWithRetryAndTimeout()?.items ?: emptyList()

    fun applicationDeployment(namespace: String, name: String) =
        openShiftClient.applicationDeployment(namespace, name).blockWithRetryAndTimeout()
            ?: throw IllegalArgumentException("No application deployment found")

    fun projects(): List<Project> = openShiftClient.projects().blockWithRetryAndTimeout()?.items ?: emptyList()

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

private fun <T> Mono<T>.blockWithRetryAndTimeout() =
    this.retryExponentialBackoff(3, Duration.ofMillis(10)) {
        logger.info("Retrying failed request, ${it.exception().message}")
    }.block(Duration.ofSeconds(5))
