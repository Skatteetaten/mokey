package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationResource::class)
@RequestMapping("/api/application")
class ApplicationController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationController::class.java)

    val assembler = ApplicationResourceAssembler()

    @GetMapping
    fun getApplications(@RequestParam("affiliation") affiliation: List<String>): MutableList<ApplicationResource> {
        return assembler.toResources(applicationDataService.findAllApplicationData(affiliation))
    }
}

class ApplicationResourceAssembler : ResourceAssemblerSupport<ApplicationData, ApplicationResource>(ApplicationController::class.java, ApplicationResource::class.java) {
    override fun toResource(data: ApplicationData): ApplicationResource {
        val environment = Environment.fromNamespace(data.namespace)
        return ApplicationResource(
                data.affiliation,
                environment.name,
                data.name,
                data.auroraStatus.let { AuroraStatusResource(it.level.toString()) },
                Version(data.deployTag, data.imageDetails?.auroraVersion)
        ).apply {
            add(linkTo(ApplicationController::class.java).slash(data.id).withSelfRel())
            add(linkTo(ApplicationDetailsController::class.java).slash(data.id).withRel("ApplicationDetails"))
        }
    }
}

