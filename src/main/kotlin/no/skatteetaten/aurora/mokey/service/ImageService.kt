package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Image
import no.skatteetaten.aurora.mokey.extensions.imageStreamNameAndTag
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openShiftService: OpenShiftService) {

    /**
     * Gets ImageDetails for the first Image that is found in the ImageChange triggers for the given DeploymentConfig.
     */
    fun getImageDetails(dc: DeploymentConfig): ImageDetails? {
        val tagAndName = dc.imageStreamNameAndTag ?: return null
        return getImageDetails(dc.metadata.namespace, tagAndName.first, tagAndName.second)
    }

    fun getImageDetails(namespace: String, name: String, tagName: String): ImageDetails? {
        return openShiftService.imageStreamTag(namespace, name, tagName)?.let { istag ->

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
        val config: Map<*, *> = it["Config"] as Map<*, *>
        val envList = config["Env"] as List<String>
        envList.map { env ->
            val (key, value) = env.split("=")
            key to value
        }.toMap()
    } ?: emptyMap()