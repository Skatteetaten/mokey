package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.GroupedApplicationData
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(ApplicationController.path)
class ApplicationController(
    private val applicationDataService: ApplicationDataService,
    private val assembler: ApplicationResourceAssembler
) {

    companion object {
        const val path = "/api/application"
    }

    @GetMapping("/{applicationId}")
    fun getApplication(@PathVariable applicationId: String): ApplicationResource? =
        applicationDataService.findAllPublicApplicationDataByApplicationId(applicationId)
            .let { GroupedApplicationData.create(it).firstOrNull()?.let(assembler::toResource) }

    @GetMapping
    fun getApplications(
        @RequestParam(required = false, defaultValue = "", name = "affiliation") affiliation: List<String>,
        @RequestParam(required = false, defaultValue = "", name = "id") id: List<String>
    ): List<ApplicationResource> {
        val allApplicationData = applicationDataService.findAllPublicApplicationData(affiliation, id)
        return assembler.toResources(GroupedApplicationData.create(allApplicationData))
    }
}

@Component
class ApplicationResourceAssembler : ResourceAssemblerSupport<GroupedApplicationData, ApplicationResource>() {

    private val applicationDeploymentAssembler = ApplicationDeploymentResourceAssembler()

    override fun toResource(data: GroupedApplicationData): ApplicationResource {
        val applicationDeployments = data.applications.map(applicationDeploymentAssembler::toResource)
        return ApplicationResource(
            data.applicationId,
            data.name,
            applicationDeployments
        )
    }

}
