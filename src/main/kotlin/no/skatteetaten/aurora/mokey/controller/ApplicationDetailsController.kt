package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
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

    fun toResource(dataResult: Either<ManagementEndpointError, ManagementData>): ValueOrManagementError<ManagementDataResource> {

        val errorMapper: (ManagementEndpointError) -> ManagementEndpointErrorResource = { e ->
            ManagementEndpointErrorResource(
                    message = e.message,
                    code = e.code,
                    endpoint = e.endpoint,
                    rootCause = e.rootCause
            )
        }

        fun <T> eitherToOr(either: Either<ManagementEndpointError, T>): ValueOrManagementError<T> =
                either.fold({ ValueOrManagementError(error = errorMapper(it)) }, { ValueOrManagementError(it) })

        return dataResult.fold(
                right = {
                    ValueOrManagementError(ManagementDataResource(
                            eitherToOr(it.info),
                            eitherToOr(it.health),
                            eitherToOr(it.env)
                    ))
                },
                left = { ValueOrManagementError(error = errorMapper(it)) }
        )
    }

    return ApplicationDetailsResource(
            toApplication(it),

            ImageDetailsResource(
                    it.imageDetails?.dockerImageReference,
                    it.imageDetails?.imageBuildTime,
                    it.imageDetails?.environmentVariables
            ),
            it.pods.map { PodResource(toResource(it.managementData)) }
    )
}

