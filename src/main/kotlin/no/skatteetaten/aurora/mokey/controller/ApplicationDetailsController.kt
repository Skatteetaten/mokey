package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.NoAccessException
import no.skatteetaten.aurora.mokey.service.NoSuchResourceException
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/applicationdetails")
class ApplicationDetailsController(val auroraApplicationCacheService: AuroraApplicationCacheService, val openShiftService: OpenShiftService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationDetailsController::class.java)

    @GetMapping()
    fun get(@PathVariable namespace: String, @PathVariable application: String, @AuthenticationPrincipal user: User): ApplicationDetails? {

        if (!openShiftService.currentUserHasAccess(namespace)) {
            throw NoAccessException("User=${user.username} with name=${user.fullName} and tokenSnippet=${user.tokenSnippet} does not have access to project=${namespace}")
        }

        val applicationId = ApplicationId(application, Environment.fromNamespace(namespace))
        return auroraApplicationCacheService.get(applicationId)?.let {
            ApplicationDetails(toApplication(it))
        } ?: throw NoSuchResourceException("Does not exist")
    }
}

