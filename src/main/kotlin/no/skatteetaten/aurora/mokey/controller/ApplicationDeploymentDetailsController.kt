package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.Link
import org.springframework.hateoas.mvc.ControllerLinkBuilder
import org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationDeploymentDetailsResource::class)
@RequestMapping("/api/auth/applicationdeploymentdetails")
class ApplicationDeploymentDetailsController(
    val applicationDataService: ApplicationDataService,
    val assembler: ApplicationDeploymentDetailsResourceAssembler
) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationDeploymentDetailsController::class.java)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal user: User): ApplicationDeploymentDetailsResource? =
        applicationDataService.findApplicationDataByApplicationDeploymentId(id)
            ?.let(assembler::toResource)
            ?: throw NoSuchResourceException("Does not exist")

    @GetMapping

    fun getAll(
        @RequestParam(required = false, defaultValue = "", name = "affiliation") affiliation: List<String>,
        @RequestParam(required = false, defaultValue = "", name = "id") id: List<String>
    ): List<ApplicationDeploymentDetailsResource> =
        assembler.toResources(applicationDataService.findAllApplicationData(affiliation, id))
}

@Component
class ApplicationDeploymentDetailsResourceAssembler(val linkBuilder: LinkBuilder) :
    ResourceAssemblerSupport<ApplicationData, ApplicationDeploymentDetailsResource>(
        ApplicationDeploymentDetailsController::class.java,
        ApplicationDeploymentDetailsResource::class.java
    ) {

    override fun toResource(applicationData: ApplicationData): ApplicationDeploymentDetailsResource {

        val infoResponse = applicationData.firstInfoResponse

        return ApplicationDeploymentDetailsResource(
            id = applicationData.applicationDeploymentId,
            buildTime = infoResponse?.buildTime,
            gitInfo = toGitInfoResource(infoResponse),
            imageDetails = applicationData.imageDetails?.let { toImageDetailsResource(it) },
            podResources = applicationData.pods.map { toPodResource(applicationData, it) },
            dependencies = infoResponse?.dependencies ?: emptyMap(),
            applicationDeploymentCommand = toDeploymentCommandResource(applicationData.deploymentCommand)
        ).apply {

            this.add(createApplicationLinks(applicationData))
        }
    }

    private fun toImageDetailsResource(imageDetails: ImageDetails) =
        ImageDetailsResource(imageDetails.imageBuildTime, imageDetails.dockerImageReference)

    private fun toPodResource(applicationData: ApplicationData, podDetails: PodDetails): PodResource {
        val pod = podDetails.openShiftPodExcerpt

        val podLinks = podDetails.managementData.let { managementData ->
            val podManagementLinks = managementData.info?.deserialized?.podLinks
            podManagementLinks?.map { createPodLink(applicationData, podDetails, it.value, it.key) }
        } ?: listOf()

        val consoleLinks = linkBuilder.openShiftConsoleLinks(pod.name, applicationData.namespace)

        val managementResponsesResource = podDetails.managementData.let {
            managementData ->
                val links = toHttpResponseResource(managementData.links, podDetails)
                val health = managementData.health?.let { toHttpResponseResource(managementData.health, podDetails) }
                val info = managementData.info?.let { toHttpResponseResource(managementData.info, podDetails) }
                val env = managementData.env?.let { toHttpResponseResource(managementData.env, podDetails) }

                ManagementResponsesResource(links, health, info, env)
            }

        return PodResource(
            pod.name,
            pod.status,
            pod.restartCount,
            pod.ready,
            pod.startTime,
            managementResponsesResource
        ).apply {
            this.add(podLinks + consoleLinks)
        }
    }

    private fun <T> toHttpResponseResource(result: ManagementEndpointResult<T>, podDetails: PodDetails): HttpResponseResource {
        return if (result.isSuccess) {
            HttpResponseResource(hasResponse = true, textResponse = result.textResponse, createdAt = result.createdAt)
        } else {
            HttpResponseResource(hasResponse = false, error = ManagementEndpointErrorResource(
                    podName = podDetails.openShiftPodExcerpt.name,
                    message = result.textResponse,
                    endpoint = result.endpointType,
                    url = result.url,
                    code = result.code,
                    rootCause = result.rootCause
            ))
        }
    }

    private fun toGitInfoResource(aPod: InfoResponse?) =
        GitInfoResource(aPod?.commitId, aPod?.commitTime)

    private fun toDeploymentCommandResource(deploymentCommand: ApplicationDeploymentCommand) =
        ApplicationDeploymentCommandResource(
            overrideFiles = deploymentCommand.overrideFiles,
            applicationDeploymentRef = ApplicationDeploymentRefResource(
                deploymentCommand.applicationDeploymentRef.environment,
                deploymentCommand.applicationDeploymentRef.application
            ),
            auroraConfig = AuroraConfigRefResource(
                deploymentCommand.auroraConfig.name,
                deploymentCommand.auroraConfig.refName,
                deploymentCommand.auroraConfig.resolvedRef
            )
        )

    private fun createApplicationLinks(applicationData: ApplicationData): List<Link> {

        val selfLink =
            ControllerLinkBuilder.linkTo(ApplicationDeploymentDetailsController::class.java)
                .slash(applicationData.applicationDeploymentId)
                .withSelfRel()
        val addressLinks =
            applicationData.addresses.map { linkBuilder.createLink(it.url.toString(), it::class.simpleName!!) }
        val serviceLinks = applicationData.firstInfoResponse?.serviceLinks
            ?.map { createServiceLink(applicationData, it.value, it.key) }
            ?: emptyList()

        val deploymentSpecLinks = linkBuilder.deploymentSpec(applicationData.deploymentCommand)

        val filesLinks = linkBuilder.files(applicationData.deploymentCommand)

        val applyResultLink = if (applicationData.booberDeployId != null)
            linkBuilder.applyResult(
                applicationData.deploymentCommand.auroraConfig.name,
                applicationData.booberDeployId
            ) else null

        val applyLink = linkBuilder.apply(deploymentCommand = applicationData.deploymentCommand)
        val auroraConfigFileLinks = linkBuilder.auroraConfigFile(deploymentCommand = applicationData.deploymentCommand)

        val applicationDeploymentRel =
            linkTo(ApplicationDeploymentController::class.java).slash(applicationData.applicationDeploymentId)
                .withRel("ApplicationDeployment")

        val applicationRel = linkTo(ApplicationController::class.java).slash(applicationData.applicationId)
            .withRel("Application")
        return (serviceLinks + addressLinks + applyResultLink + deploymentSpecLinks + filesLinks + applyLink +
            auroraConfigFileLinks + applicationDeploymentRel + applicationRel + selfLink).filterNotNull()
    }

    private fun createServiceLink(applicationData: ApplicationData, link: String, rel: String) =
        linkBuilder.createLink(link, rel, applicationData.expandParams)

    private fun createPodLink(
        applicationData: ApplicationData,
        podDetails: PodDetails,
        link: String,
        rel: String
    ): Link {
        val podExpandParams = podDetails.expandParams
        val applicationExpandParams = applicationData.expandParams
        return linkBuilder.createLink(link, rel, applicationExpandParams + podExpandParams)
    }
}

private val ApplicationData.firstInfoResponse: InfoResponse?
    get() {
        val pod = this.pods.firstOrNull()
        return pod?.managementData?.info?.deserialized
    }