package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.*
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

    fun toHealthEndpointResponse(it: HealthResponse): Map<String, Any> {
        return mutableMapOf<String, Any>("status" to it.status).also { response: MutableMap<String, Any> ->
            it.parts.forEach { checkName: String, healthPart: HealthPart ->
                val details = mutableMapOf<String, Any>("status" to healthPart.status)
                        .apply { this.putAll(healthPart.details) }
                response.put(checkName, details)
            }
        }
    }

    val errorMapper: (ManagementEndpointError) -> ManagementEndpointErrorResource = { e ->
        ManagementEndpointErrorResource(
                message = e.message,
                code = e.code,
                endpoint = e.endpoint,
                rootCause = e.rootCause
        )
    }

    fun <F, T> eitherToOr(either: Either<ManagementEndpointError, F>, valueMapper: (from: F) -> T): ValueOrManagementError<T> =
            either.fold(
                    { it: ManagementEndpointError -> ValueOrManagementError(error = errorMapper(it)) },
                    { ValueOrManagementError(valueMapper.invoke(it)) }
            )

    fun toManagementDataResource(dataResult: Either<ManagementEndpointError, ManagementData>): ValueOrManagementError<ManagementDataResource> {

        return eitherToOr(dataResult, {
            ManagementDataResource(
                    eitherToOr(it.health) { toHealthEndpointResponse(it) },
                    eitherToOr(it.env) { it }
            )
        })
    }

    // This is slightly nightmareish. We want the Info endpoint response to be an application level property and not
    // a instance/pod level property because all the instances will return the same information from this endpoint.
    // However, creating a proper response object through the nested Either objects was extremely clunky. I think this
    // information actually could be combined with the ImageDetailsResource to provide a combined details resource.
    val infoResponse = it.pods.firstOrNull()
            ?.let {
                eitherToOr(it.managementData) {
                    eitherToOr(it.info) { InfoResponseResource(it.buildTime, it.commitId, it.commitTime, it.dependencies, it.podLinks, it.serviceLinks) }
                }
            }?.let {
                ValueOrManagementError(it.value?.value, it.error ?: it.value?.error)
            }
    return ApplicationDetailsResource(
            toApplicationResource(it),

            ImageDetailsResource(
                    it.imageDetails?.dockerImageReference,
                    it.imageDetails?.imageBuildTime,
                    it.imageDetails?.environmentVariables
            ),
            infoResponse,
            it.pods.map { PodResource(toManagementDataResource(it.managementData)) }
    )
}

