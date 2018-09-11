package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationDeploymentResource::class)
@RequestMapping("/api/applicationdeployment")
class ApplicationDeploymentController(
    val applicationDataService: ApplicationDataService
) {

    val assembler = ApplicationDeploymentResourceAssembler()

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ApplicationDeploymentResource {
        val application =
            applicationDataService.findApplicationDataByApplicationDeploymentId(id)
                ?: throw NoSuchResourceException("Does not exist")
        return assembler.toResource(application)
    }
}

class ApplicationDeploymentResourceAssembler :
    ResourceAssemblerSupport<ApplicationData, ApplicationDeploymentResource>(
        ApplicationDeploymentController::class.java,
        ApplicationDeploymentResource::class.java
    ) {

    override fun toResource(applicationData: ApplicationData): ApplicationDeploymentResource {
        val environment = Environment.fromNamespace(applicationData.namespace, applicationData.affiliation)
        return ApplicationDeploymentResource(
            applicationData.applicationDeploymentId,
            applicationData.affiliation,
            environment.name,
            environment.namespace,
            applicationData.applicationDeploymentName,
            applicationData.auroraStatus.let { status ->
                AuroraStatusResource(
                    status.level.name,
                    status.comment,
                    applicationData.auroraStatus.statuses.map {
                        HealthStatusDetailResource(
                            it.level.name,
                            it.comment,
                            it.ref
                        )
                    }
                )
            },
            Version(applicationData.deployTag, applicationData.imageDetails?.auroraVersion)
        ).apply {
            add(linkTo(ApplicationDeploymentController::class.java).slash(applicationData.applicationDeploymentId).withSelfRel())
        }
    }
}