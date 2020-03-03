package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.api.model.Pod
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.EndpointType.DISCOVERY
import no.skatteetaten.aurora.mokey.model.EndpointType.ENV
import no.skatteetaten.aurora.mokey.model.EndpointType.HEALTH
import no.skatteetaten.aurora.mokey.model.EndpointType.INFO
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.reactive.function.client.WebClientResponseException

private val logger = KotlinLogging.logger {}

// TODO: This entire abstraction can go away. The client code can go into OpenShiftManagemetnClient
class ManagementEndpoint(val pod: Pod, val port: Int, val path: String, val endpointType: EndpointType) {

    val url = "namespaces/${pod.metadata.namespace}/pods/${pod.metadata.name}:$port/proxy/$path"

    // A lot of this error handling should be moved into the client
    fun <S : Any> findJsonResource(client: OpenShiftManagementClient, clazz: Class<S>): ManagementEndpointResult<S> {

        // logger.debug("Getting managementResource for uri={}", url)

        // TODO: Previously this had a timeout of 2s, what is it now?
        val response = try {
            val response = runBlocking { client.proxyManagementInterfaceRaw(pod, port, path) }
            // logger.debug("Response status=OK body={}", response)
            HttpResponse(response, 200)
        } catch (e: WebClientResponseException) {
            val errorResponse = HttpResponse(String(e.responseBodyAsByteArray), e.statusCode.value())
            // logger.debug("Respone url=$url status=ERROR body={}", errorResponse.content)
            if (e.statusCode.is5xxServerError) {
                errorResponse
            } else {
                return toManagementEndpointResultAsError(exception = e, response = errorResponse)
            }
        } catch (e: Exception) {
            return toManagementEndpointResultAsError(exception = e)
        }

        val deserialized = try {
            jacksonObjectMapper().readValue(response.content, clazz)
        } catch (e: Exception) {
            return toManagementEndpointResultAsError(exception = e, response = response)
        }

        return toManagementEndpointResultAsSuccess(deserialized = deserialized, response = response)
    }

    // TODO: This method can disappear
    fun <T : Any> findJsonResource(
        client: OpenShiftManagementClient,
        parser: (node: JsonNode) -> T
    ): ManagementEndpointResult<T> {

        val intermediate = this.findJsonResource(client, JsonNode::class.java)

        return intermediate.deserialized?.let { deserialized ->
            try {
                toManagementEndpointResultAsSuccess(
                    deserialized = parser(deserialized),
                    response = intermediate.response
                )
            } catch (e: Exception) {
                // TODO: Rpleace this with error handling in client
                toManagementEndpointResult<T>(
                    response = intermediate.response,
                    resultCode = "INVALID_FORMAT",
                    errorMessage = e.message
                )
            }
        } ?: toManagementEndpointResult(
            response = intermediate.response,
            resultCode = intermediate.resultCode,
            errorMessage = intermediate.errorMessage
        ) // TODO: replace this with error handling in client
    }

    private fun <T : Any> toManagementEndpointResult(
        deserialized: T? = null,
        response: HttpResponse? = null,
        resultCode: String,
        errorMessage: String? = null
    ): ManagementEndpointResult<T> {
        return ManagementEndpointResult(
            deserialized = deserialized,
            response = response,
            resultCode = resultCode,
            errorMessage = errorMessage,
            endpointType = this.endpointType,
            url = this.url
        )
    }

    private fun <T : Any> toManagementEndpointResultAsSuccess(
        deserialized: T?,
        response: HttpResponse?
    ): ManagementEndpointResult<T> =
        toManagementEndpointResult(
            deserialized = deserialized,
            response = response,
            resultCode = "OK"
        )

    // TODO: This has to be rewritten in the client
    private fun <T : Any> toManagementEndpointResultAsError(
        exception: Exception,
        response: HttpResponse? = null
    ): ManagementEndpointResult<T> {
        val resultCode = when (exception) {
            is HttpStatusCodeException -> "ERROR_${exception.statusCode}"
            is RestClientException -> "ERROR_HTTP"
            is JsonParseException -> "INVALID_JSON"
            is MismatchedInputException -> "INVALID_JSON"
            else -> "ERROR_UNKNOWN"
        }

        return toManagementEndpointResult(
            response = response,
            resultCode = resultCode,
            errorMessage = exception.message
        )
    }
}

// TODO This entire abstraction can go away
class ManagementInterface internal constructor(
    private val client: OpenShiftManagementClient,
    val links: ManagementLinks,
    val infoEndpoint: ManagementEndpoint? = null,
    val envEndpoint: ManagementEndpoint? = null,
    val healthEndpoint: ManagementEndpoint? = null

) {

    companion object {
        fun create(
            client: OpenShiftManagementClient,
            pod: Pod,
            path: String?
        ): Pair<ManagementInterface?, ManagementEndpointResult<ManagementLinks>> {
            if (path.isNullOrBlank()) {
                return Pair(
                    null, toManagementEndpointResultDiscoveryConfigError("Management path is missing")
                )
            }
            // TODO: validate this
            val port = path.substringBefore("/").removePrefix(":").toInt()
            val p = path.substringAfter("/")

            val discoveryEndpoint = ManagementEndpoint(pod, port, p, DISCOVERY)

            val response: ManagementEndpointResult<ManagementLinks> = discoveryEndpoint.getCachedOrCompute {
                findManagementLinks(it, client)
            }

            return response.deserialized?.let { links ->

                Pair(ManagementInterface(
                    client = client,
                    links = links,
                    infoEndpoint = links.linkFor(INFO)?.let { url -> ManagementEndpoint(pod, port, url, INFO) },
                    envEndpoint = links.linkFor(ENV)?.let { url -> ManagementEndpoint(pod, port, url, ENV) },
                    healthEndpoint = links.linkFor(HEALTH)?.let { url ->
                        ManagementEndpoint(
                            pod,
                            port,
                            url,
                            HEALTH
                        )
                    }
                ), response)
            } ?: Pair(null, response)
        }

        private fun findManagementLinks(
            discoveryEndpoint: ManagementEndpoint,
            client: OpenShiftManagementClient
        ): ManagementEndpointResult<ManagementLinks> {
            return discoveryEndpoint.findJsonResource(client) { response: JsonNode ->
                val asMap = response[DISCOVERY.key].asMap()
                val links = asMap
                    .mapValues {
                        val rawHref = it.value["href"].asText()!!
                        rawHref.replace("http://", "").substringAfter("/")
                    }
                ManagementLinks(links)
            }
        }

        // TODO: Move this into error handling when finding managementEndpoint
        fun <T : Any> toManagementEndpointResultDiscoveryConfigError(cause: String): ManagementEndpointResult<T> {
            return ManagementEndpointResult(
                errorMessage = cause,
                endpointType = EndpointType.DISCOVERY,
                resultCode = "ERROR_CONFIGURATION"
            )
        }
    }
}

// TODO: Replace this with just marshalling to jsonNode and validating that there is a status property that resolves to a valid status field
object HealthResponseParser {

    enum class HealthResponseFormat { SPRING_BOOT_1X, SPRING_BOOT_2X }

    private const val STATUS_PROPERTY = "status"
    private const val DETAILS_PROPERTY = "details"

    fun parse(json: JsonNode): HealthResponse = when (json.format) {
        HealthResponseFormat.SPRING_BOOT_2X -> handleSpringBoot2Format(json)
        else -> handleSpringBoot1Format(json)
    }

    private fun handleSpringBoot2Format(json: JsonNode): HealthResponse =
        handleSpringBootFormat(json) { it.details }

    private fun handleSpringBoot1Format(json: JsonNode): HealthResponse =
        handleSpringBootFormat(json) { it.allNodesExceptStatus }

    private fun handleSpringBootFormat(
        json: JsonNode,
        detailsExtractor: (JsonNode) -> Map<String, JsonNode>
    ): HealthResponse {
        val healthStatus = json.status
        val allDetails = detailsExtractor(json)
        val parts = allDetails.mapValues {
            val status = it.value.status
            val partDetails = detailsExtractor(it.value)
            HealthPart(status, partDetails)
        }
        return HealthResponse(healthStatus, parts)
    }

    private val JsonNode.format
        get(): HealthResponseFormat {
            return if (this.has(DETAILS_PROPERTY) && this.has(STATUS_PROPERTY) && this.size() == 2) {
                HealthResponseFormat.SPRING_BOOT_2X
            } else {
                HealthResponseFormat.SPRING_BOOT_1X
            }
        }

    private val JsonNode.details get() = this.extract("/$DETAILS_PROPERTY").asMap()

    private val JsonNode.allNodesExceptStatus
        get() = this.asMap().toMutableMap().also { it.remove(STATUS_PROPERTY) }.toMap()

    private val JsonNode.status
        get(): HealthStatus {
            return try {
                this.extract("/$STATUS_PROPERTY")?.textValue()?.let { HealthStatus.valueOf(it) }
            } catch (e: Throwable) {
                null
            } ?: throw IllegalArgumentException("Element did not contain valid $STATUS_PROPERTY property")
        }
}
