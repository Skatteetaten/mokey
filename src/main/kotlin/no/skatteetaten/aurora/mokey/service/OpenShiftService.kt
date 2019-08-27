package no.skatteetaten.aurora.mokey.service

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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service

@Service
class OpenShiftService(val openShiftClient: OpenShiftClient) {

    fun dc(namespace: String, name: String) =
        openShiftClient.deploymentConfig(namespace, name).blockForResource()

    fun route(namespace: String, name: String) =
        openShiftClient.route(namespace, name).blockForResource()

    fun routes(namespace: String, labelMap: Map<String, String>): List<Route> =
        openShiftClient.routes(namespace, labelMap).blockForList()

    fun services(namespace: String, labelMap: Map<String, String>): List<io.fabric8.kubernetes.api.model.Service> =
        openShiftClient.services(namespace, labelMap).blockForList()

    fun pods(namespace: String, labelMap: Map<String, String>): List<Pod> =
        openShiftClient.pods(namespace, labelMap).blockForList()

    fun rc(namespace: String, name: String) =
        openShiftClient.replicationController(namespace, name).blockForResource()

    fun imageStreamTag(namespace: String, name: String, tag: String) =
        openShiftClient.imageStreamTag(namespace, name, tag).blockForResource()

    fun applicationDeployments(namespace: String) =
        openShiftClient.applicationDeployments(namespace).blockForResource()?.items ?: emptyList()

    fun applicationDeployment(namespace: String, name: String) =
        openShiftClient.applicationDeployment(namespace, name).blockForResource()
            ?: throw IllegalArgumentException("No application deployment found")

    fun projects(): List<Project> = openShiftClient.projects().blockForList()

    fun projectByNamespaceForUser(namespace: String) =
        openShiftClient.project(name = namespace, token = getUserToken()).blockForResource()

    fun projectsForUser(): Set<Project> =
        openShiftClient.projects(getUserToken()).blockForList().toSet()

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
