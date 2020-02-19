package no.skatteetaten.aurora.mokey.controller

import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.authorization.resourceAttributes
import com.fkorotkov.kubernetes.authorization.spec
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newProject
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/permissions")
class PermissionController(
        @TargetClient(ClientTypes.USER_TOKEN) val client: KubernetesCoroutinesClient
) {

    @GetMapping("/{namespace}")
    fun checkPermissions(@PathVariable namespace: String): AuroraNamespacePermissions {

        return runBlocking {
            client.getOrNull(newProject {
                metadata {
                    name = namespace
                }
            })?.let {
                AuroraNamespacePermissions(
                        namespace = namespace,
                        view = true,
                        admin = canViewAndAdmin(namespace)
                )
            } ?: AuroraNamespacePermissions(namespace = namespace)
        }
    }


    fun canViewAndAdmin(namespace: String): Boolean {
        val review = newSelfSubjectAccessReview {
            spec {
                resourceAttributes {
                    this.namespace = namespace
                    verb = "update"
                    resource = "deploymentconfig"
                }
            }
        }
        return runBlocking {
            // TODO What if this 404?
            client.post(review).status?.allowed ?: false
        }
    }


}

data class AuroraNamespacePermissions(
        val view: Boolean = false,
        val admin: Boolean = false,
        val namespace: String
)
