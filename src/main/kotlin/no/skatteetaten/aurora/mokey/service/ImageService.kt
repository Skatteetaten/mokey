package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.extensions.imageStreamTag
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openshiftService: OpenShiftService) {

    fun getImageDetails(dc: DeploymentConfig): ImageDetails? {
        val imageStreamTag = dc.imageStreamTag ?: return null

        val tag = openshiftService.imageStreamTag(dc.metadata.namespace, dc.metadata.name, imageStreamTag)
        val env: Map<String, String> = tag?.image?.dockerImageMetadata?.additionalProperties?.let {
            val config: Map<String, Any> = it["ContainerConfig"] as Map<String, Any>
            val envList = config["Env"] as List<String>
            envList.map {
                val (key, value) = it.split("=")
                key to value
            }.toMap()
        } ?: emptyMap()

        val imageBuildTime = env["IMAGE_BUILD_TIME"]?.let { DateParser.parseString(it) }
        return ImageDetails(tag?.image?.dockerImageReference, imageBuildTime, env)
    }
}