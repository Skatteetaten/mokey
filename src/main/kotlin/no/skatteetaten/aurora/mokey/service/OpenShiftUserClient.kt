package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newProject
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
    suspend fun getProjectByNameOrNull(p: String): Project? = client.getOrNull(newProject {
        metadata {
            name = p
        }
    })

    suspend fun selfSubjectAccessReview(review: SelfSubjectAccessReview): SelfSubjectAccessReview = client.post(review)

    suspend fun getAllProjects(): List<Project> = client.getMany()
}
