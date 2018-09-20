package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.GroupedApplicationData
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.PodError
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.utils.value
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.Link
import org.springframework.hateoas.mvc.ControllerLinkBuilder
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
@RequestMapping("/api/applicationdeploymentdetails")
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
    fun getAll(@RequestParam affiliation: String, @AuthenticationPrincipal user: User): List<ApplicationDeploymentDetailsResource> =
        assembler.toResources(applicationDataService.findAllApplicationData(listOf(affiliation)))
}

@Component
class ApplicationDeploymentDetailsResourceAssembler(val linkBuilder: LinkBuilder) :
    ResourceAssemblerSupport<ApplicationData, ApplicationDeploymentDetailsResource>(
        ApplicationDeploymentDetailsController::class.java,
        ApplicationDeploymentDetailsResource::class.java
    ) {

    private val applicationAssembler = ApplicationResourceAssembler()

    override fun toResource(applicationData: ApplicationData): ApplicationDeploymentDetailsResource {

        val errorResources = applicationData.errors.map(this::toErrorResource)
        val infoResponse = applicationData.firstInfoResponse
        return ApplicationDeploymentDetailsResource(
            infoResponse?.buildTime,
            toGitInfoResource(infoResponse),
            applicationData.imageDetails?.let { toImageDetailsResource(it) },
            applicationData.pods.mapNotNull { toPodResource(applicationData, it) },
            infoResponse?.dependencies ?: emptyMap(),
            toDeploymentCommandResource(applicationData.deploymentCommand),
            errorResources
        ).apply {
            embedResource("Application", applicationAssembler.toResource(GroupedApplicationData(applicationData)))
            this.add(createApplicationLinks(applicationData))
        }
    }

    private fun toImageDetailsResource(imageDetails: ImageDetails) =
        ImageDetailsResource(imageDetails.imageBuildTime, imageDetails.dockerImageReference)

    private fun toPodResource(applicationData: ApplicationData, podDetails: PodDetails): PodResource {
        val podLinks = podDetails.managementData.value?.let { managementData ->
            val podManagementLinks = managementData.info.value?.podLinks
            podManagementLinks?.map { createPodLink(applicationData, podDetails, it.value, it.key) }
        } ?: listOf()

        val pod = podDetails.openShiftPodExcerpt
        return PodResource(
            pod.name,
            pod.status,
            pod.restartCount,
            pod.ready,
            pod.startTime
        ).apply {
            this.add(podLinks)
        }
    }

    private fun toGitInfoResource(aPod: InfoResponse?) =
        GitInfoResource(aPod?.commitId, aPod?.commitTime)

    private fun toDeploymentCommandResource(deploymentCommand: ApplicationDeploymentCommand) =
        ApplicationDeploymentCommandResource(
            deploymentCommand.overrideFiles,
            ApplicationDeploymentRefResource(
                deploymentCommand.applicationDeploymentRef.environment,
                deploymentCommand.applicationDeploymentRef.application
            ),
            AuroraConfigRefResource(
                deploymentCommand.auroraConfig.name,
                deploymentCommand.auroraConfig.refName,
                deploymentCommand.auroraConfig.resolvedRef
            )
        )

    private fun toErrorResource(podError: PodError): ManagementEndpointErrorResource {
        val podName = podError.podDetails.openShiftPodExcerpt.name
        return podError.error
            .let {
                ManagementEndpointErrorResource(
                    podName,
                    it.message,
                    it.endpointType,
                    it.url,
                    it.code,
                    it.rootCause
                )
            }
    }

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

        return (serviceLinks + addressLinks + applyResultLink + deploymentSpecLinks + filesLinks + selfLink).filterNotNull()
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
        return pod?.managementData?.value?.info?.value
    }