package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewResourceAttributes
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewSpec
import no.skatteetaten.aurora.openshift.webclient.OpenShiftClient
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.retry.retryExponentialBackoff
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    fun dc(namespace: String, name: String) =
        openShiftClient.deploymentConfig(namespace, name).handleError().retryWithLog().block()

    fun route(namespace: String, name: String) =
        openShiftClient.route(namespace, name).handleError().retryWithLog().block()

    fun routes(namespace: String, labelMap: Map<String, String>): List<Route> =
        openShiftClient.routes(namespace, labelMap).handleError().retryWithLog().block()?.blockList()

    fun services(namespace: String, labelMap: Map<String, String>): List<io.fabric8.kubernetes.api.model.Service> =
        openShiftClient.services(namespace, labelMap).handleError().retryWithLog().block()?.items ?: emptyList()

    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> =
        openShiftClient.pods(namespace, labelMap).handleError().retryWithLog().block()?.items ?: emptyList()

    fun rc(namespace: String, name: String) =
        openShiftClient.replicationController(namespace, name).handleError().retryWithLog().block()

    fun imageStreamTag(namespace: String, name: String, tag: String) =
        openShiftClient.imageStreamTag(namespace, name, tag).handleError().retryWithLog().block()

    fun applicationDeployments(namespace: String) =
        openShiftClient.applicationDeployments(namespace).handleError().retryWithLog().block()?.items ?: emptyList()

    fun applicationDeployment(namespace: String, name: String) =
        openShiftClient.applicationDeployment(namespace, name).handleError().retryWithLog().block()
            ?: throw IllegalArgumentException("No application deployment found")

    fun projects(): List<Project> = openShiftClient.projects().handleError().retryWithLog().block()?.items ?: emptyList()

    fun projectByNamespaceForUser(namespace: String) =
        openShiftClient.project(name = namespace, token = getUserToken()).handleError().retryWithLog().block()

    fun projectsForUser(): Set<Project> =
        openShiftClient.projects(getUserToken()).handleError().retryWithLog().block()?.items?.toSet() ?: emptySet()

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
        return openShiftClient.selfSubjectAccessView(review).block()?.status?.allowed ?: false
    }

    private fun getUserToken() = (SecurityContextHolder.getContext().authentication.principal as User).token
}

fun <T> Mono<T>.handleError() = this.onErrorResume {
        when (it) {
            is WebClientResponseException.NotFound -> {
                logger.info { "Resource not found: ${it.request?.uri.toString()}" }
                Mono.empty()
            }
            else -> Mono.error(it)
        }
    }

fun <T> Mono<T>.retryWithLog() = this.retryExponentialBackoff(3, Duration.ofMillis(10)) {
    logger.debug {
        val e = it.exception()
        val msg = "Retrying failed request, ${e.message}"
        if (e is WebClientResponseException) {
            "$msg, url: ${e.request?.uri.toString()}"
        } else {
            msg
        }
    }
}

fun <T : KubernetesResourceList<*>> T.blockList() = this.items ?: emptyList()
