package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

//TODO: Switch to v2
@Service
class DockerService(val httpClient: RestTemplate, val objectMapper: ObjectMapper) {

    val logger: Logger = LoggerFactory.getLogger(DockerService::class.java)

    companion object {
        val DOCKER_IMAGE_MANIFEST: MediaType = MediaType.valueOf("application/vnd.docker.container.image.v1+json ")

    }

    fun getEnv(registryUrl: String, name: String, tag: String, token: String? = null): Map<String, String>? {

        val manifestURI = generateManifestURI(registryUrl, name, tag)
        try {
            return getImageManifest(manifestURI, token)?.let {
                val manifestHistory = it.at("/history/0/v1Compatibility").asText()
                val history = objectMapper.readTree(manifestHistory)
                val env = history.at("/container_config/Env") as ArrayNode
                val envMap = mutableMapOf<String, String>()
                env.elements().forEachRemaining {
                    val (key, value) = it.textValue().split("=")
                    envMap[key] = value
                }
                envMap
            }
        }catch(e:Exception) {
            logger.warn("Failed getting docker manifest with url=${manifestURI}")
            return null
        }
    }

    fun getImageManifest(url:URI, token: String?): JsonNode? {
        val headers = HttpHeaders()
        headers.accept = listOf(DOCKER_IMAGE_MANIFEST)
        token?.let {
            headers.set("Authorization", "Bearer $token")
        }
        return getManifestWithHeaders(url, headers)
    }

    fun getManifestWithHeaders(url: URI, headers: HttpHeaders): JsonNode? {

        logger.debug("henter manifest url={}", url)
        val req = RequestEntity<String>(headers, HttpMethod.GET, url)

        val res = httpClient.exchange(req, String::class.java)

        return res.body?.let {
            objectMapper.readTree(it)
        }
    }

    fun generateManifestURI(registryUrl: String, name: String, tag: String) = URI("$registryUrl/v2/$name/manifests/$tag")


}