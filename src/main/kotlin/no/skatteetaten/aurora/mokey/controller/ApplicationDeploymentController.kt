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
            applicationData.affiliation,
            environment.name,
            environment.namespace,
            applicationData.auroraStatus.let { AuroraStatusResource(it.level.toString(), it.comment) },
            Version(applicationData.deployTag, applicationData.imageDetails?.auroraVersion)
        ).apply {
            add(linkTo(ApplicationDeploymentController::class.java).slash(applicationData.applicationDeploymentId).withSelfRel())
        }
    }
}