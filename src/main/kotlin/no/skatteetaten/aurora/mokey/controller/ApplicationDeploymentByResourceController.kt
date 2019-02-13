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
@RequestMapping("/api/auth/applicationdeploymentbyresource")
class ApplicationDeploymentByResourceController(
    val applicationDataService: ApplicationDataServiceCacheDecorator,
    val applicationDeploymentResourceAssembler: ApplicationDeploymentResourceAssembler
) {
    val logger: Logger = LoggerFactory.getLogger(ApplicationDeploymentByResourceController::class.java)

    @PostMapping("/databases")
    fun getApplicationDeploymentsForDatabases(@RequestBody payload: DatabaseResourcePayload): List<ApplicationDeploymentsWithDbResource> {
        val allApplicationData = applicationDataService.getAllApplicationDataFromCache().filter {
            it.databases.isNotEmpty()
        }

        return payload.databaseIds.map { id ->
            val applicationDeployments = allApplicationData.filter { it.databases.contains(id) }.map {
                applicationDeploymentResourceAssembler.toResource(it.publicData)
            }

            ApplicationDeploymentsWithDbResource(id, applicationDeployments)
        }
    }
}

data class DatabaseResourcePayload(val databaseIds: List<String>)

data class ApplicationDeploymentsWithDbResource(
    val databaseId: String,
    val applicationDeployments: List<ApplicationDeploymentResource>
)
