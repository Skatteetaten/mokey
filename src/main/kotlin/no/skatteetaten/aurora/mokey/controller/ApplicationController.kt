package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.GroupedApplicationData
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationResource::class)
@RequestMapping("/api/application")
class ApplicationController(
    private val applicationDataService: ApplicationDataService,
    private val assembler: ApplicationResourceAssembler
) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationController::class.java)

    @GetMapping("/{applicationId}")
    fun getApplication(@PathVariable applicationId: String): ApplicationResource? =
        applicationDataService.findAllPublicApplicationDataByApplicationId(applicationId)
            .let { GroupedApplicationData.create(it).firstOrNull()?.let(assembler::toResource) }

    @GetMapping
    fun getApplications(
        @RequestParam(required = false, defaultValue = "", name = "affiliation") affiliation: List<String>,
        @RequestParam(required = false, defaultValue = "", name = "id") id: List<String>
    ): List<ApplicationResource> {
        val allApplicationData = applicationDataService.findAllPublicApplicationData(affiliation, id)
        return assembler.toResources(GroupedApplicationData.create(allApplicationData))
    }
}

@Component
class ApplicationResourceAssembler :
    ResourceAssemblerSupport<GroupedApplicationData, ApplicationResource>(
        ApplicationController::class.java,
        ApplicationResource::class.java
    ) {

    private val applicationDeploymentAssembler = ApplicationDeploymentResourceAssembler()

    override fun toResource(data: GroupedApplicationData): ApplicationResource {

        val applicationDeployments = data.applications.map(applicationDeploymentAssembler::toResource)

        return ApplicationResource(
            data.applicationId,
            data.name,
            applicationDeployments
        ).apply {
            data.applicationId?.let {
                add(linkTo(ApplicationController::class.java).slash(it).withSelfRel())
            }
        }
    }
}
