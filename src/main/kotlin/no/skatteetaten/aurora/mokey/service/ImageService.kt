package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Image
import no.skatteetaten.aurora.mokey.extensions.imageStreamTag
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openShiftService: OpenShiftService) {

    /**
     * Gets ImageDetails for the first Image that is found in the ImageChange triggers for the given DeploymentConfig.
     */
    fun getImageDetails(dc: DeploymentConfig): ImageDetails? {
        val tagName = dc.imageStreamTag ?: return null
        return getImageDetails(dc.metadata.namespace, dc.metadata.name, tagName)
    }

    fun getImageDetails(namespace: String, name: String, tagName: String): ImageDetails? {
        return openShiftService.imageStreamTagWebClient(namespace, name, tagName)?.let { istag ->

            val dockerTagReference = istag.tag?.from?.name
            val image = istag.image
            val env = image.env
            val imageBuildTime = (
                env["IMAGE_BUILD_TIME"]
                    ?: image?.dockerImageMetadata?.additionalProperties?.getOrDefault("Created", null) as String?
                )?.let(DateParser::parseString)
            ImageDetails(image.dockerImageReference, dockerTagReference, imageBuildTime, env)
        }
    }
}

val Image.env: Map<String, String>
    get() = dockerImageMetadata?.additionalProperties?.let {
        val config: Map<*, *> = it["ContainerConfig"] as Map<*, *>
        val envList = config["Env"] as List<String>
        envList.map {
            val (key, value) = it.split("=")
            key to value
        }.toMap()
    } ?: emptyMap()