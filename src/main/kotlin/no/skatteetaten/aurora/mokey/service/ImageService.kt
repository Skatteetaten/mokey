package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.Image
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openShiftService: OpenShiftService, val imageRegistryService: ImageRegistryService) {

    fun getImageDetails(
        namespace: String,
        imageSteamName: String,
        image: String
    ): ImageDetails? {
        val imageTagResource = image.replace("@", "/").let { sha ->
            imageRegistryService.findTagsByName(listOf(sha))
        }

        val env: Map<String, String> = mapOf(
            "AURORA_VERSION" to imageTagResource.auroraVersion,
            "APP_VERSION" to imageTagResource.appVersion,
            "IMAGE_BUILD_TIME" to imageTagResource.timeline.buildStarted.toString(),
            "DOCKER_VERSION" to imageTagResource.dockerVersion,
            "DOCKER_DIGEST" to imageTagResource.dockerDigest,
            "JAVA_VERSION_MAJOR" to imageTagResource.java?.major,
            "JAVA_VERSION_MINOR" to imageTagResource.java?.minor,
            "JAVA_VERSION_BUILD" to imageTagResource.java?.build,
            "REQUEST_URL" to imageTagResource.requestUrl
        ).filterNullValues()

        return ImageDetails(image, null, null, env)
    }

    fun getImageDetailsFromImageStream(namespace: String, name: String, tagName: String): ImageDetails? {
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
