package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.hateoas.ExposesResourceFor
import org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo
import org.springframework.hateoas.mvc.ResourceAssemblerSupport
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@ExposesResourceFor(ApplicationInstanceResource::class)
@RequestMapping("/api/applicationinstance")
class ApplicationInstanceController(
    val applicationDataService: ApplicationDataService
) {

    val assembler = ApplicationInstanceResourceAssembler()

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ApplicationInstanceResource {
        val application =
            applicationDataService.findApplicationDataByInstanceId(id)
                ?: throw NoSuchResourceException("Does not exist")
        return assembler.toResource(application)
    }
}

class ApplicationInstanceResourceAssembler :
    ResourceAssemblerSupport<ApplicationData, ApplicationInstanceResource>(
        ApplicationInstanceController::class.java,
        ApplicationInstanceResource::class.java
    ) {

    override fun toResource(applicationData: ApplicationData): ApplicationInstanceResource {
        val environment = Environment.fromNamespace(applicationData.namespace, applicationData.affiliation)
        return ApplicationInstanceResource(
            applicationData.affiliation,
            environment.name,
            environment.namespace,
            applicationData.auroraStatuses.currentStatus.let { status ->
                StatusResource(
                    status.level.name,
                    status.comment,
                    AuroraStatusDetailsResource(
                        applicationData.auroraStatuses.deploymentStatuses.map {
                            AuroraStatusResource(
                                it.level.name,
                                it.comment
                            )
                        },
                        applicationData.auroraStatuses.podStatuses.mapValues {
                            AuroraStatusResource(
                                it.value.level.name,
                                it.value.comment
                            )
                        }.toMap()
                    )
                )
            },
            Version(applicationData.deployTag, applicationData.imageDetails?.auroraVersion)
        ).apply {
            add(linkTo(ApplicationInstanceController::class.java).slash(applicationData.applicationInstanceId).withSelfRel())
        }
    }
}