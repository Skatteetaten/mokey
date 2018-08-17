package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
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
@ExposesResourceFor(ApplicationInstanceDetailsResource::class)
@RequestMapping("/api/applicationinstancedetails")
class ApplicationInstanceDetailsController(
    val applicationDataService: ApplicationDataService,
    val assembler: ApplicationInstanceDetailsResourceAssembler
) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationInstanceDetailsController::class.java)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal user: User): ApplicationInstanceDetailsResource? =
        applicationDataService.findApplicationDataByInstanceId(id)
            ?.let(assembler::toResource)
            ?: throw NoSuchResourceException("Does not exist")

    @GetMapping
    fun getAll(@RequestParam affiliation: String, @AuthenticationPrincipal user: User): List<ApplicationInstanceDetailsResource> =
        assembler.toResources(applicationDataService.findAllApplicationData(listOf(affiliation)))
}

@Component
class ApplicationInstanceDetailsResourceAssembler(val linkBuilder: LinkBuilder) :
    ResourceAssemblerSupport<ApplicationData, ApplicationInstanceDetailsResource>(
        ApplicationInstanceDetailsController::class.java,
        ApplicationInstanceDetailsResource::class.java
    ) {

    private val applicationAssembler = ApplicationResourceAssembler()

    override fun toResource(applicationData: ApplicationData): ApplicationInstanceDetailsResource {

        val errorResources = applicationData.errors.map(this::toErrorResource)
        val infoResponse = applicationData.firstInfoResponse
        return ApplicationInstanceDetailsResource(
            infoResponse?.buildTime,
            toGitInfoResource(infoResponse),
            applicationData.imageDetails?.let { toImageDetailsResource(it) },
            applicationData.pods.mapNotNull { toPodResource(applicationData, it) },
            infoResponse?.dependencies ?: emptyMap(),
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
            ControllerLinkBuilder.linkTo(ApplicationInstanceDetailsController::class.java)
                .slash(applicationData.applicationInstanceId)
                .withSelfRel()
        val addressLinks =
            applicationData.addresses.map { linkBuilder.createLink(it.url.toString(), it::class.simpleName!!) }
        val serviceLinks = applicationData.firstInfoResponse?.serviceLinks
            ?.map { createServiceLink(applicationData, it.value, it.key) }
            ?: emptyList()

        val deploymentSpecLink = linkBuilder.deploymentSepc(applicationData.command)

        // TODO: We should use AuroraConfig name instead of affiliation here.
        val applyResultLink = if (applicationData.booberDeployId != null)
            linkBuilder.applyResult(applicationData.command.auroraConfig.name, applicationData.booberDeployId) else null

        return (serviceLinks + addressLinks + applyResultLink + deploymentSpecLink + selfLink).filterNotNull()
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