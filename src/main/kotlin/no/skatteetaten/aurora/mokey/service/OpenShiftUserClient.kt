package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newNamespace
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReview
import io.fabric8.openshift.api.model.Project
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
import org.springframework.stereotype.Service

@Service
class OpenShiftUserClient(
    @TargetClient(ClientTypes.USER_TOKEN) val client: KubernetesCoroutinesClient
) {
    suspend fun getNamespaceByNameOrNull(p: String): Namespace? = client.getOrNull(newNamespace {
        metadata {
            name = p
        }
    })

    // TODO: Does this work on kubernetes?
    suspend fun selfSubjectAccessReview(review: SelfSubjectAccessReview): SelfSubjectAccessReview = client.post(review)

    // TODO: This will not work on kubernets, and you cannot fetch all namespaces either...
    suspend fun getAllProjects(): List<Project> = client.getMany()
}
