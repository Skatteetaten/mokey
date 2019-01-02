package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
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
            applicationDataService.findPublicApplicationDataByApplicationDeploymentId(id)
                ?: throw NoSuchResourceException("Does not exist")
        return assembler.toResource(application)
    }
}

class ApplicationDeploymentResourceAssembler :
    ResourceAssemblerSupport<ApplicationPublicData, ApplicationDeploymentResource>(
        ApplicationDeploymentController::class.java,
        ApplicationDeploymentResource::class.java
    ) {

    override fun toResource(applicationData: ApplicationPublicData): ApplicationDeploymentResource {
        val environment = Environment.fromNamespace(applicationData.namespace, applicationData.affiliation)
        return ApplicationDeploymentResource(
            id = applicationData.applicationDeploymentId,
            affiliation = applicationData.affiliation,
            environment = environment.name,
            namespace = environment.namespace,
            name = applicationData.applicationDeploymentName,
            status = applicationData.auroraStatus.let { status ->
                AuroraStatusResource(
                    code = status.level.name,
                    reasons = toStatusCheckReportResource(status.reasons),
                    reports = toStatusCheckReportResource(status.reports)
                )
            },
            version = Version(applicationData.deployTag, applicationData.auroraVersion, applicationData.releaseTo),
            time = applicationData.time,
            dockerImageRepo = applicationData.dockerImageRepo
        ).apply {
            add(linkTo(ApplicationDeploymentController::class.java).slash(applicationData.applicationDeploymentId).withSelfRel())
            add(
                linkTo(ApplicationDeploymentDetailsController::class.java)
                    .slash(applicationData.applicationDeploymentId)
                    .withRel(resourceClassNameToRelName(ApplicationDeploymentDetailsResource::class))
            )
            add(
                linkTo(ApplicationController::class.java)
                    .slash(applicationData.applicationId)
                    .withRel(resourceClassNameToRelName(ApplicationResource::class))
            )
        }
    }

    private fun toStatusCheckReportResource(list: List<StatusCheckReport>) = list.map {
        StatusCheckReportResource(
            name = it.name,
            description = it.description,
            failLevel = it.failLevel,
            hasFailed = it.hasFailed
        )
    }
}