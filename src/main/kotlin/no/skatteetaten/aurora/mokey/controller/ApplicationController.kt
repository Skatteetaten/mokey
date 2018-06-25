package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.GroupedApplicationData
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
        val allApplicationData = applicationDataService.findAllApplicationData(affiliation)
        return assembler.toResources(GroupedApplicationData.create(allApplicationData))
    }
}

class ApplicationResourceAssembler :
    ResourceAssemblerSupport<GroupedApplicationData, ApplicationResource>(
        ApplicationController::class.java,
        ApplicationResource::class.java
    ) {
    override fun toResource(data: GroupedApplicationData): ApplicationResource {
        val applicationInstances = data.applications.map {
            val environment = Environment.fromNamespace(it.namespace, it.affiliation)
            ApplicationInstanceResource(
                it.affiliation,
                environment.name,
                it.namespace,
                it.auroraStatus.let { status -> AuroraStatusResource(status.level.toString(), status.comment) },
                Version(it.deployTag, it.imageDetails?.auroraVersion)
            ).apply {
                add(linkTo(ApplicationInstanceController::class.java).slash(it.id).withSelfRel())
                add(linkTo(ApplicationInstanceDetailsController::class.java).slash(it.id).withRel("ApplicationInstanceDetails"))
            }
        }

        return ApplicationResource(
            data.name,
            emptyList(),
            applicationInstances
        )
    }
}
