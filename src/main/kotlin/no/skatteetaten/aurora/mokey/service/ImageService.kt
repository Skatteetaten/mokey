package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.Image
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.stereotype.Service

@Service
class ImageService(val openShiftService: OpenShiftService, val imageRegistryService: ImageRegistryService) {

    /**
     * Gets ImageDetails for the first Image that is found in the ImageChange triggers for the given DeploymentConfig.
     */
    fun getImageDetails(
        namespace: String,
        imageSteamName: String,
        isLatest: Boolean,
        rc: ReplicationController?
    ): ImageDetails? {

        if (!isLatest) {
            rc?.let {
                val rawSha = rc.spec.template.spec.containers[0].image?.let {
                    it.replace("@", "/").let { sha ->
                        imageRegistryService.findTagsByName(listOf(sha))
                    }
                }
                val envVar = rawSha?.items?.get(0)

                val env = mapOf(
                    "AURORA_VERSION" to (envVar?.auroraVersion ?: ""),
                    "APP_VERSION" to (envVar?.appVersion ?: ""),
                    "IMAGE_BUILD_TIME" to (envVar?.timeline?.buildStarted.toString()),
                    "DOCKER_VERSION" to (envVar?.dockerVersion ?: ""),
                    "DOCKER_DIGEST" to (envVar?.dockerDigest ?: ""),
                    "JAVA_VERSION_MAJOR" to (envVar?.java?.major ?: ""),
                    "JAVA_VERSION_MINOR" to (envVar?.java?.minor ?: ""),
                    "JAVA_VERSION_BUILD" to (envVar?.java?.build ?: ""),
                    "REQUEST_URL" to (envVar?.requestUrl ?: "")
                )
                return ImageDetails(rc.spec.template.spec.containers[0].image, null, null, env)
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