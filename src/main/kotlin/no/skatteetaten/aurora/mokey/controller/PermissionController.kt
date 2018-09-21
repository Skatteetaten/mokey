package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/permissions")
class PermissionController(val openShiftService: OpenShiftService) {

    val logger: Logger = LoggerFactory.getLogger(PermissionController::class.java)

    @GetMapping("/{namespace}")
    fun checkPermissions(@PathVariable namespace: String): AuroraNamespacePermissions {

        return openShiftService.projectByNamespaceForUser(namespace)?.let {

            logger.info("{}", it)
            AuroraNamespacePermissions(
                namespace = namespace,
                view = true,
                admin = openShiftService.canViewAndAdmin(namespace)
            )
        } ?: throw RuntimeException("Illegal project with name=$namespace")
    }

    data class AuroraNamespacePermissions(
        val view: Boolean = true,
        val admin: Boolean = false,
        val namespace: String
    )
}
