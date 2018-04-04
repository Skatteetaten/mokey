package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.*
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.fold
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.Link
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@ExposesResourceFor(ApplicationDetailsResource::class)
@RequestMapping("/api/applicationdetails")
class ApplicationDetailsController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationDetailsController::class.java)

    val assembler = ApplicationDetailsResourceAssembler()

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

class ApplicationDetailsResourceAssembler : ResourceAssemblerSupport<ApplicationData, ApplicationDetailsResource>(ApplicationDetailsResource::class.java, ApplicationDetailsResource::class.java) {

    private val applicationAssembler = ApplicationResourceAssembler()

    override fun toResource(applicationData: ApplicationData): ApplicationDetailsResource {

        val aPod = applicationData.pods.firstOrNull()
        val anInfoResponse = aPod?.let {
            mappedValueOrError(it.managementData) {
                mappedValueOrError(it.info) { it }
            }
        }?.let { it.value?.value }

        return ApplicationDetailsResource(
                applicationAssembler.toResource(applicationData),
                toBuildInfoResource(anInfoResponse, applicationData.imageDetails),
                applicationData.imageDetails?.let { toImageDetailsResource(it) },
                applicationData.pods.mapNotNull { toPodResource(it) },
                anInfoResponse?.dependencies ?: emptyMap()
        ).apply {
            anInfoResponse?.serviceLinks
                    ?.map { Link(it.value, it.key) }
                    ?.forEach(this::add)
        }
    }

    private fun toImageDetailsResource(imageDetails: ImageDetails): ImageDetailsResource {
        return ImageDetailsResource(imageDetails.dockerImageReference)
    }

    private fun toPodResource(podDetails: PodDetails): PodResource? {

        return mappedValueOrError(podDetails.managementData, {
            val podLinks = mappedValueOrError(it.info) { it.podLinks }.value
            PodResource(podDetails.openShiftPodExcerpt.name).apply { podLinks?.map { Link(it.value, it.key) }?.forEach(this::add) }
        }).value
    }

    private fun toBuildInfoResource(aPod: InfoResponse?, imageDetails: ImageDetails?): BuildInfoResource? {
        return BuildInfoResource(imageDetails?.imageBuildTime, aPod?.buildTime, aPod?.commitId, aPod?.commitTime)
    }

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
