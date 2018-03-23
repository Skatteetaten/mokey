package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openshiftService: OpenShiftService) {

    fun getImageDetails(dc: DeploymentConfig): ImageDetails? {
        val deployTag = dc.spec.triggers.find { it.type == "ImageChange" }
                ?.imageChangeParams?.from?.name?.split(":")?.lastOrNull()
                ?: return null

        val tag = openshiftService.imageStreamTag(dc.metadata.namespace, dc.metadata.name, deployTag)
        val environmentVariables = tag?.image?.dockerImageMetadata?.containerConfig?.env?.map {
            val (key, value) = it.split("=")
            key to value
        }?.toMap()
        return ImageDetails(tag?.image?.dockerImageReference, environmentVariables ?: mapOf())
    }
}