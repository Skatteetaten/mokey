package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.model.PodDetails
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

    @GetMapping
    fun getAll(@RequestParam affiliation: String, @AuthenticationPrincipal user: User): List<ApplicationDetailsResource> {

        return assembler.toResources(applicationDataService.findAllApplicationData(listOf(affiliation)))
    }
}

@Component
class ApplicationDetailsResourceAssembler(val linkBuilder: LinkBuilder)
    : ResourceAssemblerSupport<ApplicationData, ApplicationDetailsResource>(ApplicationDetailsController::class.java, ApplicationDetailsResource::class.java) {

    private val applicationAssembler = ApplicationResourceAssembler()

    override fun toResource(applicationData: ApplicationData): ApplicationDetailsResource {

        val aPod = applicationData.pods.firstOrNull()
        val anInfoResponse = aPod?.let {
            mappedValueOrError(it.managementData) {
                mappedValueOrError(it.info) { it }
            }
        }?.let { it.value?.value }

        return ApplicationDetailsResource(
                toBuildInfoResource(anInfoResponse, applicationData.imageDetails),
                applicationData.imageDetails?.let { toImageDetailsResource(it) },
                applicationData.pods.mapNotNull { toPodResource(it) },
                anInfoResponse?.dependencies ?: emptyMap()
        ).apply {
            embedResource("Application", applicationAssembler.toResource(applicationData))

            val selfLink = ControllerLinkBuilder.linkTo(ApplicationDetailsController::class.java).slash(applicationData.id).withSelfRel()
            val serviceLinks = (anInfoResponse?.serviceLinks ?: emptyMap()).map { Link(it.value, it.key) }
            val addressLinks = applicationData.addresses.map { Link(it.url.toString(), it::class.simpleName!!) }
            // TODO: We should use AuroraConfig name instead of affiliation here.
            val applyResultLink = linkBuilder.applyResult(applicationData.affiliation, applicationData.booberDeployId)

            (serviceLinks + addressLinks + applyResultLink + selfLink).filterNotNull().forEach(this::add)
        }
    }

    private fun toImageDetailsResource(imageDetails: ImageDetails) =
            ImageDetailsResource(imageDetails.dockerImageReference)

    private fun toPodResource(podDetails: PodDetails) =
            mappedValueOrError(podDetails.managementData, {
                val podLinks = mappedValueOrError(it.info) { it.podLinks }.value
                PodResource(podDetails.openShiftPodExcerpt.name).apply { podLinks?.map { Link(it.value, it.key) }?.forEach(this::add) }
            }).value

    private fun toBuildInfoResource(aPod: InfoResponse?, imageDetails: ImageDetails?) =
            BuildInfoResource(imageDetails?.imageBuildTime, aPod?.buildTime, aPod?.commitId, aPod?.commitTime)

    private fun <F, T> mappedValueOrError(either: Either<ManagementEndpointError, F>, valueMapper: (from: F) -> T): ValueOrManagementError<T> =
            either.fold(
                    { it: ManagementEndpointError ->
                        ValueOrManagementError(error = ({ e: ManagementEndpointError ->
                            ManagementEndpointErrorResource(
                                    message = e.message,
                                    code = e.code,
                                    endpoint = e.endpoint,
                                    rootCause = e.rootCause
                            )
                        })(it))
                    },
                    { ValueOrManagementError(valueMapper.invoke(it)) }
            )
}
