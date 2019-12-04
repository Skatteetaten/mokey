package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.Image
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openShiftService: OpenShiftService, val imageRegistryService: ImageRegistryService) {

    /**
     * Gets ImageDetails for the first Image that is found in the ImageChange triggers for the given DeploymentConfig if latest rc is runnig.
     * If latest rc is not running, it calls cantus to gets the ImageDetails from the running rc.
     */
    fun getImageDetails(
        namespace: String,
        imageSteamName: String,
        isLatestRc: Boolean,
        rc: ReplicationController?
    ): ImageDetails? {

        if (!isLatestRc) {
            rc?.let {
                val image = rc.spec.template.spec.containers[0].image

                val findTagsByNameResponse = image?.let {
                    it.replace("@", "/").let { sha ->
                        imageRegistryService.findTagsByName(listOf(sha))
                    }
                }
                val imageTagResource = findTagsByNameResponse?.items?.get(0)

                val env: Map<String, String> = mapOf(
                    "AURORA_VERSION" to imageTagResource?.auroraVersion,
                    "APP_VERSION" to imageTagResource?.appVersion,
                    "IMAGE_BUILD_TIME" to imageTagResource?.timeline?.buildStarted.toString(),
                    "DOCKER_VERSION" to imageTagResource?.dockerVersion,
                    "DOCKER_DIGEST" to imageTagResource?.dockerDigest,
                    "JAVA_VERSION_MAJOR" to imageTagResource?.java?.major,
                    "JAVA_VERSION_MINOR" to imageTagResource?.java?.minor,
                    "JAVA_VERSION_BUILD" to imageTagResource?.java?.build,
                    "REQUEST_URL" to imageTagResource?.requestUrl
                ).filterNullValues()

                return ImageDetails(image, null, null, env)
            }
        }
        return getImageDetails(namespace, imageSteamName, "default")
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

fun <K, V> Map<out K, V?>.filterNullValues(): Map<K, V> {
    val result = LinkedHashMap<K, V>()
    for (entry in this) {
        entry.value?.let {
            result[entry.key] = it
        }
    }
    return result
}
