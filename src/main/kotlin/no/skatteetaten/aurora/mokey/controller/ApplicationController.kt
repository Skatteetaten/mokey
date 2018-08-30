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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationResource::class)
@RequestMapping("/api/application")
class ApplicationController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationController::class.java)

    val assembler = ApplicationResourceAssembler()

    @GetMapping("/{applicationId}")
    fun getApplication(@PathVariable applicationId: String): ApplicationResource? =
        applicationDataService.findApplicationDataByApplicationId(applicationId)
            ?.let { assembler.toResource(GroupedApplicationData(it)) }

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
        val applicationDeployements = data.applications.map { app ->
            val environment = Environment.fromNamespace(app.namespace, app.affiliation)
            ApplicationDeploymentResource(
                app.affiliation,
                environment.name,
                app.namespace,
                app.auroraStatus.let { status ->
                    AuroraStatusResource(
                        status.level.toString(),
                        status.comment,
                        app.auroraStatus.statuses.map {
                            HealthStatusDetailResource(
                                it.level.name,
                                it.comment,
                                it.ref
                            )
                        }
                    )
                },
                Version(app.deployTag, app.imageDetails?.auroraVersion)
            ).apply {
                add(linkTo(ApplicationDeploymentController::class.java).slash(app.applicationDeploymentId).withSelfRel())
                add(
                    linkTo(ApplicationDeploymentDetailsController::class.java).slash(app.applicationDeploymentId).withRel(
                        "ApplicationDeploymentDetails"
                    )
                )
            }
        }

        return ApplicationResource(
            data.applicationId,
            data.name,
            emptyList(),
            applicationDeployements
        ).apply {
            data.applicationId?.let {
                add(linkTo(ApplicationController::class.java).slash(it).withSelfRel())
            }
        }
    }
}
