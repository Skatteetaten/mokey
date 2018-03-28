package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class ApplicationController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationController::class.java)

    @GetMapping("/application")
    fun getApplications(@RequestParam("affiliation") affiliation: List<String>): List<ApplicationResource> {
        return applicationDataService.findAllApplicationData(affiliation).map(::toApplicationResource)
    }
}

fun toApplicationResource(data: ApplicationData): ApplicationResource {
    val environment = Environment.fromNamespace(data.namespace)
    return ApplicationResource(
            data.id,
            data.affiliation,
            environment.name,
            data.name,
            data.auroraStatus.let { AuroraStatusResource(it.level.toString()) },
            Version(data.deployTag, data.imageDetails?.auroraVersion)
    )
}


