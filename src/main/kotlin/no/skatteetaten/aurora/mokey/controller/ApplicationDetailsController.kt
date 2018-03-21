package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.model.Result
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
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

    fun toResource(dataResult: Result<ManagementData, ManagementEndpointError>): Result<ManagementDataResource, ManagementEndpointErrorResource> {

        val errorMapper: (ManagementEndpointError) -> ManagementEndpointErrorResource = { e ->
            ManagementEndpointErrorResource(
                    message = e.message,
                    code = e.code,
                    endpoint = e.endpoint,
                    rootCause = e.rootCause
            )
        }
        return dataResult.map(
                valueMapper = { v ->
                    ManagementDataResource(
                            info = v.info.map({ it -> it }, errorMapper),
                            health = v.health.map({ it -> it }, errorMapper)
                    )
                },
                errorMapper = errorMapper
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

