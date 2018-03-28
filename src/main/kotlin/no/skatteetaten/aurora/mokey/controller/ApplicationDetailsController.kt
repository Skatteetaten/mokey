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

    fun toManagementDataResource(dataResult: Either<ManagementEndpointError, ManagementData>): ValueOrManagementError<ManagementDataResource> {

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

        return dataResult.fold(
                right = {
                    ValueOrManagementError(ManagementDataResource(
                            eitherToOr(it.info) { InfoResponseResource(it.buildTime, it.commitId, it.commitTime, it.dependencies, it.podLinks, it.serviceLinks) },
                            eitherToOr(it.health) { toHealthEndpointResponse(it) },
                            eitherToOr(it.env) { it }
                    ))
                },
                left = { ValueOrManagementError(error = errorMapper(it)) }
        )
    }

    return ApplicationDetailsResource(
            toApplicationResource(it),

            ImageDetailsResource(
                    it.imageDetails?.dockerImageReference,
                    it.imageDetails?.imageBuildTime,
                    it.imageDetails?.environmentVariables
            ),
            it.pods.map { PodResource(toManagementDataResource(it.managementData)) }
    )
}

