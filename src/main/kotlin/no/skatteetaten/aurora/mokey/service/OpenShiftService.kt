package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.Route
import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewResourceAttributes
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewSpec
import no.skatteetaten.aurora.openshift.webclient.OpenShiftClient
import no.skatteetaten.aurora.openshift.webclient.blockForList
import no.skatteetaten.aurora.openshift.webclient.blockForResource
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class OpenShiftService(
    @Value("\${mokey.retry.first:100}") val firstRetry: Long,
    @Value("\${mokey.retry.max:2000}") val maxRetry: Long,
    val openShiftClient: OpenShiftClient
) {

    fun dc(namespace: String, name: String) =
        openShiftClient.serviceAccount().deploymentConfig(namespace, name).blockForResourceWithTimeout()

    fun route(namespace: String, name: String) =
        openShiftClient.serviceAccount().route(namespace, name).blockForResourceWithTimeout()

    fun routes(namespace: String, labelMap: Map<String, String>): List<Route> =
        openShiftClient.serviceAccount().routes(namespace, labelMap).blockForListWithTimeout()

    fun services(namespace: String, labelMap: Map<String, String>): List<io.fabric8.kubernetes.api.model.Service> =
        openShiftClient.serviceAccount().services(namespace, labelMap).blockForListWithTimeout()

    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> =
        openShiftClient.serviceAccount().pods(namespace, labelMap).blockForListWithTimeout()

    fun rc(namespace: String, name: String) =
        openShiftClient.serviceAccount().replicationController(namespace, name).blockForResourceWithTimeout()

    fun imageStreamTag(namespace: String, name: String, tag: String) =
        openShiftClient.serviceAccount().imageStreamTag(namespace, name, tag).blockForResourceWithTimeout()

    fun applicationDeployments(namespace: String) =
        openShiftClient.serviceAccount().applicationDeployments(namespace).blockForResourceWithTimeout()?.items ?: emptyList()

    fun applicationDeployment(namespace: String, name: String) =
        openShiftClient.serviceAccount().applicationDeployment(namespace, name).blockForResourceWithTimeout()
            ?: throw IllegalArgumentException("No application deployment found")

    fun projects(): List<Project> = openShiftClient.serviceAccount().projects().blockForListWithTimeout()

    fun projectByNamespaceForUser(namespace: String) =
        openShiftClient.userToken(getUserToken()).project(namespace).blockForResourceWithTimeout()

    fun projectsForUser(): Set<Project> =
        openShiftClient.userToken(getUserToken()).projects().blockForListWithTimeout().toSet()

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
        return openShiftClient.serviceAccount().selfSubjectAccessView(review).block()?.status?.allowed ?: false
    }

    fun user(token: String) = openShiftClient.userToken(token).user().blockForResourceWithTimeout()

    private fun getUserToken() = (SecurityContextHolder.getContext().authentication.principal as User).token

    private fun <T> Mono<T>.blockForResourceWithTimeout() = this.blockForResource(firstRetry, maxRetry)
    private fun <T : HasMetadata?> Mono<out KubernetesResourceList<T>>.blockForListWithTimeout() =
        this.blockForList(firstRetry, maxRetry)
}
