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
        val applicationInstances = data.applications.map { app ->
            val environment = Environment.fromNamespace(app.namespace, app.affiliation)
            ApplicationInstanceResource(
                app.affiliation,
                environment.name,
                app.namespace,
                app.auroraStatuses.currentStatus.let { status ->
                    StatusResource(
                        status.level.toString(),
                        status.comment,
                        AuroraStatusDetailsResource(
                            app.auroraStatuses.deploymentStatuses.map {
                                AuroraStatusResource(
                                    it.level.name,
                                    it.comment
                                )
                            },
                            app.auroraStatuses.podStatuses.map {
                                it.key to AuroraStatusResource(
                                    it.value.level.name,
                                    it.value.comment
                                )
                            }.toMap()
                        )
                    )
                },
                Version(app.deployTag, app.imageDetails?.auroraVersion)
            ).apply {
                add(linkTo(ApplicationInstanceController::class.java).slash(app.applicationInstanceId).withSelfRel())
                add(linkTo(ApplicationInstanceDetailsController::class.java).slash(app.applicationInstanceId).withRel("ApplicationInstanceDetails"))
            }
        }

        return ApplicationResource(
            data.applicationId,
            data.name,
            emptyList(),
            applicationInstances
        ).apply {
            data.applicationId?.let {
                add(linkTo(ApplicationController::class.java).slash(it).withSelfRel())
            }
        }
    }
}
