package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.q3c.rest.hal.HalLink

@ExperimentalStdlibApi
@RestController
@RequestMapping(ApplicationDeploymentDetailsController.path)
class ApplicationDeploymentDetailsController(
    val applicationDataService: ApplicationDataService,
    val assembler: ApplicationDeploymentDetailsResourceAssembler,
) {
    @GetMapping("/{id}")
    suspend fun get(
        @PathVariable id: String,
    ): ApplicationDeploymentDetailsResource? = applicationDataService.findApplicationDataByApplicationDeploymentId(id)
        ?.let(assembler::toResource)
        ?: throw NoSuchResourceException("Does not exist")

    @GetMapping
    suspend fun getAll(
        @RequestParam(required = false, defaultValue = "", name = "affiliation") affiliation: List<String>,
        @RequestParam(required = false, defaultValue = "", name = "id") id: List<String>,
    ): List<ApplicationDeploymentDetailsResource> = assembler.toResources(
        applicationDataService.findAllApplicationData(affiliation, id)
    )

    companion object {
        const val path = "/api/auth/applicationdeploymentdetails"
    }
}

@Component
class ApplicationDeploymentDetailsResourceAssembler(
    val linkBuilder: LinkBuilder
) : ResourceAssemblerSupport<ApplicationData, ApplicationDeploymentDetailsResource>() {
    @ExperimentalStdlibApi
    override fun toResource(entity: ApplicationData): ApplicationDeploymentDetailsResource {
        val infoResponse = entity.firstInfoResponse
        val serviceLinks = entity.firstInfoResponse?.serviceLinks
            ?.map { createServiceLink(entity, it.value, it.key) }
            ?: emptyList()

        return ApplicationDeploymentDetailsResource(
            id = entity.applicationDeploymentId,
            updatedBy = entity.deployDetails?.updatedBy,
            buildTime = infoResponse?.buildTime,
            gitInfo = toGitInfoResource(infoResponse),
            imageDetails = entity.imageDetails?.let { toImageDetailsResource(it) },
            deployDetails = entity.deployDetails?.let { toDeployDetailsResource(it) },
            podResources = entity.pods.map { toPodResource(entity, it) },
            dependencies = infoResponse?.dependencies ?: emptyMap(),
            applicationDeploymentCommand = toDeploymentCommandResource(entity.deploymentCommand),
            databases = entity.databases,
            serviceLinks = serviceLinks.toLinks()
        ).apply {
            createApplicationLinks(entity).forEach { (rel, href) ->
                link(rel, HalLink(href))
            }
        }
    }

    private fun toDeployDetailsResource(it: DeployDetails): DeployDetailsResource = DeployDetailsResource(
        targetReplicas = it.targetReplicas,
        availableReplicas = it.availableReplicas,
        deployment = it.deployment,
        phase = it.phase,
        deployTag = it.deployTag,
        paused = it.paused,
        scaledDown = it.scaledDown,
    )

    private fun toImageDetailsResource(imageDetails: ImageDetails) = ImageDetailsResource(
        imageDetails.imageBuildTime,
        imageDetails.dockerImageReference,
        imageDetails.dockerImageTagReference,
    )

    private fun toPodResource(
        applicationData: ApplicationData,
        podDetails: PodDetails,
    ): PodResource {
        val pod = podDetails.openShiftPodExcerpt
        val podLinks = podDetails.managementData.let { managementData ->
            val podManagementLinks = managementData.info?.deserialized?.podLinks
            podManagementLinks?.map { createPodLink(applicationData, podDetails, it.value, it.key) }
        } ?: emptyList()
        val consoleLinks = linkBuilder.openShiftConsoleLinks(pod.name, applicationData.namespace)
        val managementResponsesResource = podDetails.managementData.let { managementData ->
            val links = toHttpResponseResource(managementData.links)
            val health = managementData.health?.let { toHttpResponseResource(managementData.health) }
            val info = managementData.info?.let { toHttpResponseResource(managementData.info) }
            val env = managementData.env?.let { toHttpResponseResource(managementData.env) }

            ManagementResponsesResource(links, health, info, env)
        }

        return PodResource(
            name = pod.name,
            phase = pod.phase,
            startTime = pod.startTime,
            deployTag = pod.deployTag,
            latestDeployTag = pod.latestDeployTag,
            replicaName = pod.replicaName,
            latestReplicaName = pod.latestReplicaName,
            managementResponses = managementResponsesResource,
            containers = pod.containers.map {
                ContainerResource(
                    name = it.name,
                    state = it.state,
                    restartCount = it.restartCount,
                    image = it.image,
                    ready = it.ready,
                )
            },
        ).apply {
            podLinks.forEach {
                this.link(it.rel, HalLink(it.href))
            }

            consoleLinks.forEach {
                this.link(it)
            }
        }
    }

    private fun <T> toHttpResponseResource(
        result: ManagementEndpointResult<T>,
    ): HttpResponseResource = result.response?.let { response ->
        HttpResponseResource(
            hasResponse = true,
            textResponse = response.jsonContentOrError(),
            httpCode = response.code,
            createdAt = result.createdAt,
            url = result.url,
            error = if (!result.isSuccess) {
                ManagementEndpointErrorResource(
                    message = result.errorMessage,
                    code = result.resultCode,
                )
            } else {
                null
            },
        )
    } ?: HttpResponseResource(
        hasResponse = false,
        createdAt = result.createdAt,
        url = result.url,
        error = if (!result.isSuccess) {
            ManagementEndpointErrorResource(
                message = result.errorMessage,
                code = result.resultCode,
            )
        } else {
            null
        },
    )

    private fun toGitInfoResource(aPod: InfoResponse?) = GitInfoResource(aPod?.commitId, aPod?.commitTime)

    private fun toDeploymentCommandResource(
        deploymentCommand: ApplicationDeploymentCommand
    ) = ApplicationDeploymentCommandResource(
        overrideFiles = deploymentCommand.overrideFiles,
        applicationDeploymentRef = ApplicationDeploymentRefResource(
            deploymentCommand.applicationDeploymentRef.environment,
            deploymentCommand.applicationDeploymentRef.application,
        ),
        auroraConfig = AuroraConfigRefResource(
            deploymentCommand.auroraConfig.name,
            deploymentCommand.auroraConfig.refName,
            deploymentCommand.auroraConfig.resolvedRef,
        ),
    )

    @ExperimentalStdlibApi
    private fun createApplicationLinks(applicationData: ApplicationData): List<Link> = buildList {
        addAll(
            applicationData.addresses.map {
                linkBuilder.createLink(it.url.toString(), it::class.simpleName!!)
            }
        )
        addAll(linkBuilder.deploymentSpec(applicationData.deploymentCommand))
        addAll(linkBuilder.files(applicationData.deploymentCommand))

        if (applicationData.booberDeployId != null) {
            val applyResultLink = linkBuilder.applyResult(
                applicationData.deploymentCommand.auroraConfig.name,
                applicationData.booberDeployId,
            )

            add(applyResultLink)
        }

        add(linkBuilder.apply(deploymentCommand = applicationData.deploymentCommand))
        addAll(linkBuilder.auroraConfigFile(deploymentCommand = applicationData.deploymentCommand))
        add(
            linkBuilder.createMokeyLink(
                "ApplicationDeployment",
                "${ApplicationDeploymentController.path}/${applicationData.applicationDeploymentId}",
            )
        )
        add(
            linkBuilder.createMokeyLink(
                "Application",
                "${ApplicationController.path}/${applicationData.applicationId}",
            )
        )
    }

    private fun createServiceLink(
        applicationData: ApplicationData,
        link: String,
        rel: String,
    ) = linkBuilder.createLink(
        link,
        rel,
        applicationData.expandParams,
    )

    private fun createPodLink(
        applicationData: ApplicationData,
        podDetails: PodDetails,
        link: String,
        rel: String,
    ): Link = linkBuilder.createLink(
        link,
        rel,
        applicationData.expandParams + podDetails.expandParams,
    )
}

private val ApplicationData.firstInfoResponse: InfoResponse?
    get() {
        val pod = this.pods.firstOrNull()
        return pod?.managementData?.info?.deserialized
    }
