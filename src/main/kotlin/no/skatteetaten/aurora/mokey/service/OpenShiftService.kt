package no.skatteetaten.aurora.mokey.service

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
import reactor.core.publisher.Mono
import reactor.retry.retryExponentialBackoff
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Service
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    fun dc(namespace: String, name: String) =
        openShiftClient.deploymentConfig(namespace, name).blockWithRetry()

    fun route(namespace: String, name: String) =
        openShiftClient.route(namespace, name).blockWithRetry()

    fun routes(namespace: String, labelMap: Map<String, String>): List<Route> =
        openShiftClient.routes(namespace, labelMap).blockWithRetry()?.items ?: emptyList()

    fun services(namespace: String, labelMap: Map<String, String>): List<io.fabric8.kubernetes.api.model.Service> =
        openShiftClient.services(namespace, labelMap).blockWithRetry()?.items ?: emptyList()

    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> =
        openShiftClient.pods(namespace, labelMap).blockWithRetry()?.items ?: emptyList()

    fun rc(namespace: String, name: String) =
        openShiftClient.replicationController(namespace, name).blockWithRetry()

    fun imageStreamTag(namespace: String, name: String, tag: String) =
        openShiftClient.imageStreamTag(namespace, name, tag).blockWithRetry()

    fun applicationDeployments(namespace: String) =
        openShiftClient.applicationDeployments(namespace).blockWithRetry()?.items ?: emptyList()

    fun applicationDeployment(namespace: String, name: String) =
        openShiftClient.applicationDeployment(namespace, name).blockWithRetry()
            ?: throw IllegalArgumentException("No application deployment found")

    fun projects(): List<Project> = openShiftClient.projects().blockWithRetry()?.items ?: emptyList()

    fun projectByNamespaceForUser(namespace: String) =
        openShiftClient.project(name = namespace, token = getUserToken()).blockWithRetry()

    fun projectsForUser(): Set<Project> =
        openShiftClient.projects(getUserToken()).blockWithRetry()?.items?.toSet() ?: emptySet()

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

private fun <T> Mono<T>.blockWithRetry() =
    this.retryExponentialBackoff(3, Duration.ofMillis(10)) {
        logger.info("Retrying failed request, ${it.exception().message}")
    }.block()
