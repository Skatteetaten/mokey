package no.skatteetaten.aurora.mokey.controller

import com.fkorotkov.kubernetes.authorization.newSelfSubjectAccessReview
import com.fkorotkov.kubernetes.authorization.resourceAttributes
import com.fkorotkov.kubernetes.authorization.spec
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.mokey.service.OpenShiftUserClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/permissions")
class PermissionController(
    val client: OpenShiftUserClient
) {

    @GetMapping("/{namespace}")
    fun checkPermissions(@PathVariable namespace: String): AuroraNamespacePermissions {

        return runBlocking {
            client.getNamespaceByNameOrNull(namespace)?.let {
                AuroraNamespacePermissions(
                    namespace = namespace,
                    view = true,
                    admin = canViewAndAdmin(namespace)
                )
            } ?: AuroraNamespacePermissions(namespace = namespace)
        }
    }

    suspend fun canViewAndAdmin(namespace: String): Boolean {
        val review = newSelfSubjectAccessReview {
            spec {
                resourceAttributes {
                    this.namespace = namespace
                    verb = "update"
                    resource = "services"
                }
            }
        }
        return client.selfSubjectAccessReview(review).status?.allowed ?: false
    }
}

data class AuroraNamespacePermissions(
    val view: Boolean = false,
    val admin: Boolean = false,
    val namespace: String
)
