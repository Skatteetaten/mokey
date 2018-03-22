package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.Left
import no.skatteetaten.aurora.utils.Right
import no.skatteetaten.aurora.utils.fold
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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

    fun toResource(dataResult: Either<ManagementEndpointError, ManagementData>): Either<ManagementEndpointErrorResource, ManagementDataResource> {

        val errorMapper: (ManagementEndpointError) -> Left<ManagementEndpointErrorResource> = { e ->
            Left(ManagementEndpointErrorResource(
                    message = e.message,
                    code = e.code,
                    endpoint = e.endpoint,
                    rootCause = e.rootCause
            ))
        }


        return dataResult.fold(
                right = {
                    Right(
                            ManagementDataResource(
                                    info = it.info.fold(errorMapper, { i -> Right(i) }),
                                    health = it.health.fold(errorMapper, { i -> Right(i) })
                            )
                    )
                },
                left = errorMapper
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

