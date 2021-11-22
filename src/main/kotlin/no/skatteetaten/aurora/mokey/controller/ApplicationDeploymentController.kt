package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.q3c.rest.hal.HalLink

@RestController
@RequestMapping(ApplicationDeploymentController.path)
class ApplicationDeploymentController(
    private val applicationDataService: ApplicationDataService,
    private val assembler: ApplicationDeploymentResourceAssembler,
) {
    @ExperimentalStdlibApi
    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ApplicationDeploymentResource {
        val application = applicationDataService.findPublicApplicationDataByApplicationDeploymentId(
            id
        ) ?: throw NoSuchResourceException("Does not exist")

        return assembler.toResource(application)
    }

    @ExperimentalStdlibApi
    @PostMapping
    suspend fun getApplicationDeploymentForRefs(
        @RequestParam("cached", required = false) cached: Boolean = true,
        @RequestBody applicationDeploymentRefs: List<ApplicationDeploymentRef>,
    ): List<ApplicationDeploymentResource> {
        val applications = applicationDataService.findPublicApplicationDataByApplicationDeploymentRef(
            applicationDeploymentRefs,
            cached,
        )

        return assembler.toResources(applications)
    }

    companion object {
        const val path = "/api/applicationdeployment"
    }
}

@Component
class ApplicationDeploymentResourceAssembler :
    ResourceAssemblerSupport<ApplicationPublicData, ApplicationDeploymentResource>() {

    @ExperimentalStdlibApi
    override fun toResource(entity: ApplicationPublicData): ApplicationDeploymentResource {
        val environment = Environment.fromNamespace(entity.namespace, entity.affiliation)

        return ApplicationDeploymentResource(
            id = entity.applicationDeploymentId,
            affiliation = entity.affiliation,
            environment = environment.name,
            namespace = environment.namespace,
            name = entity.applicationDeploymentName,
            status = entity.auroraStatus.let { status ->
                AuroraStatusResource(
                    code = status.level.name,
                    reasons = toStatusCheckReportResource(status.reasons),
                    reports = toStatusCheckReportResource(status.reports)
                )
            },
            version = Version(entity.deployTag, entity.auroraVersion, entity.releaseTo),
            time = entity.time,
            dockerImageRepo = entity.dockerImageRepo,
            message = entity.message
        ).apply {
            link(
                resourceClassNameToRelName(ApplicationDeploymentDetailsResource::class),
                HalLink("${ApplicationDeploymentDetailsController.path}/${entity.applicationDeploymentId}")
            )

            link(
                resourceClassNameToRelName(ApplicationResource::class),
                HalLink("${ApplicationController.path}/${entity.applicationId}")
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
