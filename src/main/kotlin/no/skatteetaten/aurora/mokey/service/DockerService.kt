package no.skatteetaten.aurora.mokey.service



import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.RequestEntity
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import java.net.URI

@Service
class DockerService(val httpClient: RestTemplate, val objectMapper: ObjectMapper) {

    companion object {
        val DOCKER_MANIFEST_V2: MediaType = MediaType.valueOf("application/vnd.docker.distribution.manifest.v2+json")
        val DOCKER_IMAGE_MANIFEST: MediaType = MediaType.valueOf("application/vnd.docker.container.image.v1+json ")

    }


    fun getManifest(registryUrl: String, name: String, tag: String): ResponseEntity<JsonNode> {
        val headers = HttpHeaders()
        headers.accept = listOf(DOCKER_MANIFEST_V2)
        return getManifestWithHeaders(registryUrl, name, tag, headers)
    }


    fun getEnv(registryUrl: String, name: String, tag: String): Map<String, String> {

        return getImageManifest(registryUrl, name, tag).body?.let {
            val manifestHistory = it.at("/history/0/v1Compatibility").asText()
            val history = objectMapper.readTree(manifestHistory)
            return history.at("/container_config/Env").asMap().mapValues { it.value.asText() }
        } ?: emptyMap()
    }

    fun getImageManifest(registryUrl: String, name: String, tag: String): ResponseEntity<JsonNode> {
        val headers = HttpHeaders()
        headers.accept = listOf(DOCKER_IMAGE_MANIFEST)
        return getManifestWithHeaders(registryUrl, name, tag, headers)
    }

    fun getManifestWithHeaders(registryUrl: String, name: String, tag: String, headers: HttpHeaders): ResponseEntity<JsonNode> {
        val manifestURI = generateManifestURI(registryUrl, name, tag)

        val req = RequestEntity<JsonNode>(headers, HttpMethod.GET, manifestURI)

        return httpClient.exchange(req, JsonNode::class.java)

    }

    fun putManifest(registryUrl: String, name: String, tag: String, payload: JsonNode): ResponseEntity<JsonNode> {
        val manifestURI = generateManifestURI(registryUrl, name, tag)
        val headers = HttpHeaders()
        headers.contentType = DOCKER_MANIFEST_V2
        val req = RequestEntity<JsonNode>(payload, headers, HttpMethod.PUT, manifestURI)
        return httpClient.exchange(req, JsonNode::class.java)

    }

    fun generateManifestURI(registryUrl: String, name: String, tag: String) = URI("https://$registryUrl/v2/$name/manifests/$tag")


}