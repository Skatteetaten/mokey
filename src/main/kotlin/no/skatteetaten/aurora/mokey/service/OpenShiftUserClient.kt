package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newNamespace
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.authorization.SelfSubjectAccessReview
import io.fabric8.openshift.api.model.Project
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.config.ClientTypes.USER_TOKEN
import no.skatteetaten.aurora.kubernetes.config.TargetClient
import org.springframework.stereotype.Service

@Suppress("SpringJavaInjectionPointsAutowiringInspection")
@Service
class OpenShiftUserClient(@TargetClient(USER_TOKEN) val client: KubernetesCoroutinesClient) {
    suspend fun getNamespaceByNameOrNull(p: String): Namespace? = client.getOrNull(
        newNamespace {
            metadata {
                name = p
            }
        }
    )

    suspend fun selfSubjectAccessReview(review: SelfSubjectAccessReview): SelfSubjectAccessReview = client.post(review)

    // This will not work on kubernets, and you cannot fetch all namespaces either...
    suspend fun getAllProjects(): List<Project> = client.getMany()
}
