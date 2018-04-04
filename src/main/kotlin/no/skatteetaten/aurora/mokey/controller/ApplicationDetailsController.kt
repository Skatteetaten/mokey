package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.fold
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/applicationdetails")
class ApplicationDetailsController(val applicationDataService: ApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationDetailsController::class.java)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal user: User): ApplicationDetailsResource? {

        return applicationDataService.findApplicationDataById(id)
                ?.let(::toApplicationDetails)
                ?: throw NoSuchResourceException("Does not exist")
    }

    @GetMapping("")
    fun getAll(@RequestParam affiliation: String, @AuthenticationPrincipal user: User): List<ApplicationDetailsResource> {

        return applicationDataService.findAllApplicationData(listOf(affiliation))
                .map(::toApplicationDetails)
    }
}

fun toApplicationDetails(it: ApplicationData): ApplicationDetailsResource {

    fun <F, T> mappedValueOrError(either: Either<ManagementEndpointError, F>, valueMapper: (from: F) -> T): ValueOrManagementError<T> =
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

    fun toImageDetailsResource(imageDetails: ImageDetails): ImageDetailsResource {
        return ImageDetailsResource(
                imageDetails.dockerImageReference/*,
                imageDetails.environmentVariables*/
        )
    }

/*
    fun toHealthEndpointResponse(it: Either<ManagementEndpointError, HealthResponse>): ValueOrManagementError<Map<String, Any>> {
        return mappedValueOrError(it) {
            mutableMapOf<String, Any>("status" to it.status).also { response: MutableMap<String, Any> ->
                it.parts.forEach { checkName: String, healthPart: HealthPart ->
                    val details = mutableMapOf<String, Any>("status" to healthPart.status)
                            .apply { this.putAll(healthPart.details) }
                    response[checkName] = details
                }
            }
        }
    }
*/
/*

    fun toEnvEndpointResponse(env: Either<ManagementEndpointError, JsonNode>): ValueOrManagementError<JsonNode> {
        return mappedValueOrError(env) { it }
    }
*/

    fun toPodResource(podDetails: PodDetails): PodResource? {

        return mappedValueOrError(podDetails.managementData, {
            PodResource(
/*
                    toHealthEndpointResponse(it.health),
                    toEnvEndpointResponse(it.env),
*/
                    mappedValueOrError(it.info) { it.podLinks }.value
            )
        }).value
    }

    fun toBuildInfoResource(aPod: PodDetails?, imageDetails: ImageDetails?): BuildInfoResource? {

        return aPod?.let {
            mappedValueOrError(it.managementData) {
                mappedValueOrError(it.info) { BuildInfoResource(imageDetails?.imageBuildTime, it.buildTime, it.commitId, it.commitTime) }
            }
        }?.let {
            //            ValueOrManagementError(it.value?.value, it.error ?: it.value?.error)
            it.value?.value
        }
    }

    val aPod = it.pods.firstOrNull()
    val anInfoResponse = aPod?.let {
        mappedValueOrError(it.managementData) {
            mappedValueOrError(it.info) { it }
        }
    }?.let { it.value?.value }

    return ApplicationDetailsResource(
            toApplicationResource(it),
            toBuildInfoResource(aPod, it.imageDetails),
            it.imageDetails?.let { toImageDetailsResource(it) },
            it.pods.mapNotNull { toPodResource(it) },
            anInfoResponse?.dependencies ?: emptyMap(),
            anInfoResponse?.serviceLinks ?: emptyMap()
    )
}
