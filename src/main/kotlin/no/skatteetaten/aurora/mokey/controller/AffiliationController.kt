package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AffiliationController(val applicationDataService: ApplicationDataService) {
    @GetMapping("/affiliation")
    suspend fun getAffiliations(): List<String> = applicationDataService.findAllAffiliations()

    @GetMapping("/auth/affiliation")
    suspend fun getVisibleAffiliations(): List<String> = applicationDataService.findAllVisibleAffiliations()
}
