package no.skatteetaten.aurora.mokey.service

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.fabric8.openshift.api.model.Image
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.ImageDetails
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class ImageService(
    val client: OpenShiftServiceAccountClient,
    val imageRegistryService: ImageRegistryService,
    @Value("\${mokey.cantus.cache:true}") val cacheManagement: Boolean
) {

    // Does this need to be an async cache?
    val cache: Cache<String, ImageDetails?> = Caffeine.newBuilder()
        .expireAfterAccess(10, TimeUnit.MINUTES)
        .maximumSize(10000)
        .build()

    fun clearCache() = cache.invalidateAll()

    suspend fun getCachedOrFind(
        image: String
    ): ImageDetails? {
        val logger = KotlinLogging.logger {}

        if (!cacheManagement) {
            logger.trace("cache disabled")
            return getImageDetails(image)
        }

        val cachedResponse = (cache.getIfPresent(image))?.also {
            logger.debug("Found cached response for $image")
        }
        return cachedResponse ?: getImageDetails(image)?.also {
            logger.debug("Cached management interface $image")
            cache.put(image, it)
        }
    }

    suspend fun getImageDetails(
        image: String
    ): ImageDetails? {
        val imageTagResource = image.replace("@", "/").let { sha ->
            imageRegistryService.findTagsByName(listOf(sha)).first()
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

        val imageBuildTime = env["IMAGE_BUILD_TIME"]?.let(DateParser::parseString)

        return ImageDetails(image, null, imageBuildTime, env)
    }

    suspend fun getImageDetailsFromImageStream(namespace: String, name: String, tagName: String): ImageDetails? {

        return client.getImageStreamTag(namespace, name, tagName)?.let { istag ->
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
}
