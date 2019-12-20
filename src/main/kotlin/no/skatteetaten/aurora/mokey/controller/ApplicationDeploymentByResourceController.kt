package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ConditionalOnBean(ApplicationDataService::class)
@RequestMapping("/api/auth/applicationdeploymentbyresource")
class ApplicationDeploymentByResourceController(
    val applicationDataService: ApplicationDataService,
    val applicationDeploymentsWithDbResourceAssembler: ApplicationDeploymentsWithDbResourceAssembler
) {

    @PostMapping("/databases")
    fun getApplicationDeploymentsForDatabases(@RequestBody databaseIds: List<String>): List<ApplicationDeploymentsWithDbResource> {
        val allApplicationData = applicationDataService.getFromCacheForUser().filter {
            it.databases.isNotEmpty()
        }

        val idAndDeployments = databaseIds.map { id ->
            val applicationDeployments = allApplicationData.filter { it.databases.contains(id) }.map { it.publicData }
            id to applicationDeployments
        }

        return applicationDeploymentsWithDbResourceAssembler.toResources(idAndDeployments)
    }
}

@Component
class ApplicationDeploymentsWithDbResourceAssembler :
    ResourceAssemblerSupport<Pair<String, List<ApplicationPublicData>>, ApplicationDeploymentsWithDbResource>() {

    private val applicationDeploymentAssembler = ApplicationDeploymentResourceAssembler()

    override fun toResource(entity: Pair<String, List<ApplicationPublicData>>): ApplicationDeploymentsWithDbResource {

        val applicationDeploymentsResources = entity.second.map {
            applicationDeploymentAssembler.toResource(it)
        }

        return ApplicationDeploymentsWithDbResource(entity.first, applicationDeploymentsResources)
    }
}
