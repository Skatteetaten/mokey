package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.AuroraPublicApplication
import no.skatteetaten.aurora.mokey.model.Response
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/aurora")
class AuroraApplicationController(val auroraApplicationCacheService: AuroraApplicationCacheService, val openShiftService: OpenShiftService) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationController::class.java)

    /*
    @GetMapping("/public")
    fun publicApplications(@RequestParam("affiliation") affiliation: List<String>): List<AuroraPublicApplication> {

        return auroraApplicationCacheService.getAppsInAffiliations(affiliation)

    } */

    @GetMapping("/namespace/{namespace}/application/{application}")
    fun get(@PathVariable namespace: String, @PathVariable application: String, @AuthenticationPrincipal user: User): AuroraApplication {

        if (!openShiftService.currentUserHasAccess(namespace)) {
            throw NoAccessException("User=${user.username} with name=${user.fullName} and tokenSnippet=${user.tokenSnippet} does not have access to project=${namespace}")
        }

        logger.debug("finner applikasjon")
        val appKey = "$namespace/$application"
        return auroraApplicationCacheService.get(appKey) ?: throw NoSuchResourceException("Does not exist")
    }
}


