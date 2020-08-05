package no.skatteetaten.aurora.mokey.controller

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api")
class AffiliationController(val applicationDataService: ApplicationDataService) {

    @GetMapping("/affiliation")
    fun getAffiliations(): List<String> =
        applicationDataService.findAllAffiliations()

    @GetMapping("/auth/affiliation")
    fun getVisibleAffiliations(): List<String> {
        logger.debug("Finding all affiliations")
        return applicationDataService.findAllVisibleAffiliations()
    }
}
