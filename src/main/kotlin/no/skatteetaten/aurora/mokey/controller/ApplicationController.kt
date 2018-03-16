package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.AuroraStatusCalculator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ApplicationController(val auroraApplicationCacheService: AuroraApplicationCacheService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationController::class.java)

    @GetMapping("/application")
    fun getApplications(@RequestParam("affiliation") affiliation: List<String>): List<Application> {
        return auroraApplicationCacheService.getAppsInAffiliations(affiliation).map(::toApplication)
    }
}

fun toApplication(data: ApplicationData): Application {
    val environment = Environment.fromNamespace(data.namespace)
    return Application(
        ApplicationId(data.name, environment).toString(),
        data.name,
        environment.name,
        AuroraStatusCalculator.calculateStatus(data).copy(comment = "N/A"),
        Version(data.deployTag, data.auroraVersion)
    )
}


