package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/affiliation")
class AffiliationController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(AffiliationController::class.java)

    @GetMapping
    fun getAffiliations(): List<String> {
        return applicationDataService.findAllAffiliations()
    }
}
