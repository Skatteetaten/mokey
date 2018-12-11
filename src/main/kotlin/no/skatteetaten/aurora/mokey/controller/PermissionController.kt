package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/permissions")
class PermissionController(val openShiftService: OpenShiftService) {

    val logger: Logger = LoggerFactory.getLogger(PermissionController::class.java)

    @GetMapping("/{namespace}")
    fun checkPermissions(@PathVariable namespace: String): AuroraNamespacePermissions {

        return openShiftService.projectByNamespaceForUser(namespace)?.let {
            AuroraNamespacePermissions(
                namespace = namespace,
                view = true,
                admin = openShiftService.canViewAndAdmin(namespace)
            )
        } ?: AuroraNamespacePermissions(namespace = namespace)
    }

    data class AuroraNamespacePermissions(
        val view: Boolean = false,
        val admin: Boolean = false,
        val namespace: String
    )
}
