package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationDeploymentsWithDbResource::class)
@RequestMapping("/api/auth/applicationdeploymentbyresource")
@ConditionalOnProperty(name = ["mokey.cache.enabled"], matchIfMissing = true)
class ApplicationDeploymentByResourceController(
    val applicationDataService: ApplicationDataServiceCacheDecorator,
    val applicationDeploymentsWithDbResourceAssembler: ApplicationDeploymentsWithDbResourceAssembler
) {
    val logger: Logger = LoggerFactory.getLogger(ApplicationDeploymentByResourceController::class.java)

    @GetMapping("/databases")
    fun getApplicationDeploymentsForDatabases(
        @RequestParam(
            required = true,
            defaultValue = "",
            name = "databaseids"
        ) databaseIds: List<String>
    ): List<ApplicationDeploymentsWithDbResource> {
        val allApplicationData = applicationDataService.getAllApplicationDataFromCache().filter {
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
    ResourceAssemblerSupport<Pair<String, List<ApplicationPublicData>>, ApplicationDeploymentsWithDbResource>(
        Pair::class.java,
        ApplicationDeploymentsWithDbResource::class.java
    ) {

    private val applicationDeploymentAssembler = ApplicationDeploymentResourceAssembler()

    override fun toResource(entity: Pair<String, List<ApplicationPublicData>>): ApplicationDeploymentsWithDbResource {

        val applicationDeploymentsResources = entity.second.map {
            applicationDeploymentAssembler.toResource(it)
        }

        return ApplicationDeploymentsWithDbResource(entity.first, applicationDeploymentsResources)
    }
}
