package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth/affiliation")
class AffiliationAuthenticatedController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(AffiliationAuthenticatedController::class.java)

    @GetMapping
    fun getAffiliations(): List<String> =
        applicationDataService.findAllVisibleAffiliations()
}
