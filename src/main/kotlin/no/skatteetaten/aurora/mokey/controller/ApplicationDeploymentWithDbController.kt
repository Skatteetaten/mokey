package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationDeployment::class)
@RequestMapping("/api/auth/applicationdeploymentwithdb")
class ApplicationDeploymentWithDbController(
    val applicationDataService: ApplicationDataServiceCacheDecorator,
    val applicationDeploymentResourceAssembler: ApplicationDeploymentResourceAssembler
) {
    val logger: Logger = LoggerFactory.getLogger(ApplicationDeploymentWithDbController::class.java)

    @PostMapping
    fun getApplicationForDatabaseID(@RequestBody payload: ApplicationDeploymentPayload): List<ApplicationDeploymentWithDbResource> {
        val applications = applicationDataService.findAllApplicationData(listOf()).filter {
            it.databases.isNotEmpty()
        }

        return payload.databaseIds.map { id ->
            val apps = applications.filter {
                it.databases.contains(id)
            }.mapNotNull { app ->
                applicationDataService.findPublicApplicationDataByApplicationDeploymentId(app.applicationDeploymentId)
                    ?.let {
                        applicationDeploymentResourceAssembler.toResource(it)
                    }
            }
            ApplicationDeploymentWithDbResource(id, apps)
        }
    }
}

data class ApplicationDeploymentPayload(val databaseIds: List<String>)

data class ApplicationDeploymentWithDbResource(
    val databaseId: String,
    val applicationDeployments: List<ApplicationDeploymentResource>
)
