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

@Service
class DockerService(val httpClient: RestTemplate, val objectMapper: ObjectMapper) {

    val logger: Logger = LoggerFactory.getLogger(DockerService::class.java)

    companion object {
        val DOCKER_IMAGE_MANIFEST: MediaType = MediaType.valueOf("application/vnd.docker.container.image.v1+json ")
        val DOCKER_IMAGE_MANIFEST_V2: MediaType = MediaType.valueOf("application/vnd.docker.distribution.manifest.v2+json")
    }

    fun getEnv(registryUrl: String, name: String, tag: String, token: String? = null): Map<String, String>? {

        val manifestURI = generateManifestURI(registryUrl, name, tag)
        return getImageManifest(manifestURI, token)
                ?.let {
                    val schemaVersion = it.at("/schemaVersion").asText()
                    if (schemaVersion == "2") {
                        getImageBlog(registryUrl, name, it.at("/config/digest").asText(), token)
                                ?.let { findEnv(it) }
                    } else {
                        val manifestHistory = it.at("/history/0/v1Compatibility").asText()
                        findEnv(objectMapper.readTree(manifestHistory))
                    }

                }
    }

    private fun findEnv(jsonNode: JsonNode): MutableMap<String, String> {
        val env = jsonNode.at("/container_config/Env") as ArrayNode
        val envMap = mutableMapOf<String, String>()
        env.elements()
                .forEachRemaining {
                    val (key, value) = it.textValue().split("=")
                    envMap[key] = value
                }
        return envMap
    }

    fun getImageBlog(registryUrl: String, name: String, blobId: String, token: String?): JsonNode? {

        val headers = HttpHeaders()
        headers.accept = listOf(DOCKER_IMAGE_MANIFEST)
        token?.let {
            headers.set("Authorization", "Bearer $token")
        }
        return getWithHeaders(URI("$registryUrl/v2/$name/blobs/$blobId"), headers)
    }

    fun getImageManifest(url: URI, token: String?): JsonNode? {
        val headers = HttpHeaders()
        headers.accept = listOf(DOCKER_IMAGE_MANIFEST_V2)
        token?.let {
            headers.set("Authorization", "Bearer $token")
        }
        return getWithHeaders(url, headers)
    }

    fun getWithHeaders(url: URI, headers: HttpHeaders = HttpHeaders()): JsonNode? {

        logger.debug("henter manifest url={}", url)
        val req = RequestEntity<String>(headers, HttpMethod.GET, url)

        val res = httpClient.exchange(req, String::class.java)

        return res.body?.let {
            objectMapper.readTree(it)
        }
    }

    fun generateManifestURI(registryUrl: String, name: String, tag: String) = URI("$registryUrl/v2/$name/manifests/$tag")
}