package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.mappedValueOrError
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.fold
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
@ExposesResourceFor(ApplicationDetailsResource::class)
@RequestMapping("/api/applicationdetails")
class ApplicationDetailsController(
        val applicationDataService: ApplicationDataService,
        val assembler: ApplicationDetailsResourceAssembler) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationDetailsController::class.java)


    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal user: User): ApplicationDetailsResource? {

        return applicationDataService.findApplicationDataById(id)
                ?.let(assembler::toResource)
                ?: throw NoSuchResourceException("Does not exist")
    }

    @GetMapping("")
    fun getAll(@RequestParam affiliation: String, @AuthenticationPrincipal user: User): List<ApplicationDetailsResource> {

        return assembler.toResources(applicationDataService.findAllApplicationData(listOf(affiliation)))
    }
}

@Component
class ApplicationDetailsResourceAssembler(val linkBuilder: LinkBuilder)
    : ResourceAssemblerSupport<ApplicationData, ApplicationDetailsResource>(ApplicationDetailsController::class.java, ApplicationDetailsResource::class.java) {

    private val applicationAssembler = ApplicationResourceAssembler()

    override fun toResource(applicationData: ApplicationData): ApplicationDetailsResource {

        val infoResponse = applicationData.firstInfoResponse
        return ApplicationDetailsResource(
                toBuildInfoResource(infoResponse, applicationData.imageDetails),
                applicationData.imageDetails?.let { toImageDetailsResource(it) },
                applicationData.pods.mapNotNull { toPodResource(applicationData, it) },
                infoResponse?.dependencies ?: emptyMap()
        ).apply {
            embedResource("Application", applicationAssembler.toResource(applicationData))
            this.add(createApplicationLinks(applicationData))
        }
    }

    private fun toImageDetailsResource(imageDetails: ImageDetails) =
            ImageDetailsResource(imageDetails.dockerImageReference)

    private fun toPodResource(applicationData: ApplicationData, podDetails: PodDetails) =
            mappedValueOrError(podDetails.managementData, {
                val podName = podDetails.openShiftPodExcerpt.name
                val podLinks = mappedValueOrError(it.info) { it.podLinks }.value
                val map = podLinks?.map { createPodLink(applicationData, podDetails, it.value, it.key) }
                PodResource(podName).apply {
                    this.add(map)
                }
            }).value

    private fun toBuildInfoResource(aPod: InfoResponse?, imageDetails: ImageDetails?) =
            BuildInfoResource(imageDetails?.imageBuildTime, aPod?.buildTime, aPod?.commitId, aPod?.commitTime)

    private fun createApplicationLinks(applicationData: ApplicationData): List<Link> {

        val selfLink = ControllerLinkBuilder.linkTo(ApplicationDetailsController::class.java).slash(applicationData.id).withSelfRel()
        val addressLinks = applicationData.addresses.map { linkBuilder.createLink(it.url.toString(), it::class.simpleName!!) }
        val serviceLinks = applicationData.firstInfoResponse?.serviceLinks
                ?.map { createServiceLink(applicationData, it.value, it.key) }
                ?: emptyList()

        // TODO: We should use AuroraConfig name instead of affiliation here.
        val applyResultLink = if (applicationData.affiliation != null && applicationData.booberDeployId != null)
            linkBuilder.applyResult(applicationData.affiliation, applicationData.booberDeployId) else null

        return (serviceLinks + addressLinks + applyResultLink + selfLink).filterNotNull()
    }

    private fun createServiceLink(applicationData: ApplicationData, link: String, rel: String) =
            linkBuilder.createLink(link, rel, applicationData.expandParams)

    private fun createPodLink(applicationData: ApplicationData, podDetails: PodDetails, link: String, rel: String): Link {
        val podExpandParams = podDetails.expandParams
        val applicationExpandParams = applicationData.expandParams
        return linkBuilder.createLink(link, rel, applicationExpandParams + podExpandParams)
    }
}


private val ApplicationData.firstInfoResponse: InfoResponse?
    get() {
        val pod = this.pods.firstOrNull()
        return pod?.let {
            mappedValueOrError(it.managementData) {
                mappedValueOrError(it.info) { it }
            }
        }?.let { it.value?.value }
    }