package no.skatteetaten.aurora.mokey.service.openshift

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.service.OpenShiftException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.net.URI

@Component
class OpenShiftResourceClient(@Value("\${openshift.url}") val baseUrl: String,
                              val tokenProvider: ServiceAccountTokenProvider,
                              val restTemplate: RestTemplate) {

    val apiLocation = "/oapi/v1/"

    val logger: Logger = LoggerFactory.getLogger(OpenShiftResourceClient::class.java)

    fun get(resourceUrl: String): ResponseEntity<JsonNode>? {

        val headers: HttpHeaders = createHeaders(tokenProvider.getToken())
        try {
            return exchange(RequestEntity<Any>(headers, HttpMethod.GET, createResourceUri(resourceUrl)))
        } catch (e: OpenShiftException) {
            if (e.cause is HttpClientErrorException && e.cause.statusCode == HttpStatus.NOT_FOUND) {
                return null
            }
            throw e
        }
    }

    private fun createResourceUri(resourceUrl: String): URI {
        return URI(baseUrl.removeSuffix("/") + apiLocation + resourceUrl.removePrefix("/"))
    }

    private fun createHeaders(token: String): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("Authorization", "Bearer " + token)
        return headers
    }

    private fun <T> exchange(requestEntity: RequestEntity<T>): ResponseEntity<JsonNode> {
        logger.info("${requestEntity.method} resource at ${requestEntity.url}")

        val createResponse: ResponseEntity<JsonNode> = try {
            restTemplate.exchange(requestEntity, JsonNode::class.java)
        } catch (e: HttpClientErrorException) {
            throw OpenShiftException("Request failed url=${requestEntity.url}, method=${requestEntity.method}, message=${e.message}, code=${e.statusCode.value()}", e)
        }
        logger.debug("Body=${createResponse.body}")
        return createResponse
    }
}