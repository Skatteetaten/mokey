package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.q3c.rest.hal.HalLink

@RestController
@RequestMapping(ApplicationDeploymentController.path)
class ApplicationDeploymentController(
    private val applicationDataService: ApplicationDataService,
    private val assembler: ApplicationDeploymentResourceAssembler
) {

    companion object {
        const val path = "/api/applicationdeployment"
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ApplicationDeploymentResource {
        val application =
            applicationDataService.findPublicApplicationDataByApplicationDeploymentId(id)
                ?: throw NoSuchResourceException("Does not exist")
        return assembler.toResource(application)
    }
}

@Component
class ApplicationDeploymentResourceAssembler :
    ResourceAssemblerSupport<ApplicationPublicData, ApplicationDeploymentResource>() {

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
            dockerImageRepo = applicationData.dockerImageRepo,
            message = applicationData.message
        ).apply {
            link(resourceClassNameToRelName(ApplicationDeploymentDetailsResource::class),
                HalLink("${ApplicationDeploymentDetailsController.path}/${applicationData.applicationDeploymentId}"))

            link(resourceClassNameToRelName(ApplicationResource::class),
                HalLink("${ApplicationController.path}/${applicationData.applicationId}"))
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
