package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newNamespace
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newProject
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview
import io.fabric8.openshift.api.model.Project
import kotlinx.coroutines.reactive.awaitFirstOrNull
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.newLabel
import no.skatteetaten.aurora.springboot.getToken
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Service

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Service
class OpenShiftUserClient(@Qualifier("managementCoroutinesClient") val client: KubernetesCoroutinesClient) {
    suspend fun getNamespaceByNameOrNull(p: String): Namespace? = client.getOrNull(
        resource = newNamespace {
            metadata {
                name = p
            }
        },
        token = getToken()
    )

    suspend fun selfSubjectAccessReview(review: SelfSubjectAccessReview): SelfSubjectAccessReview = client.post(
        resource = review,
        token = getToken()
    )

    // This will not work on kubernets, and you cannot fetch all namespaces either...
    suspend fun getAllProjects(): List<Project> = client.getMany(token = getToken())

    suspend fun getProjectsInAffiliation(affiliation: String): List<Project> = client.getMany(
        resource = newProject {
            metadata {
                labels = newLabel("affiliation", affiliation)
            }
        },
        token = getToken()
    )

    private suspend fun getToken() = ReactiveSecurityContextHolder.getContext().awaitFirstOrNull()?.authentication?.getToken()
}
