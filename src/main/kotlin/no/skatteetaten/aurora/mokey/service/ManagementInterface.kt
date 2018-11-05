package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.extract
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import no.skatteetaten.aurora.mokey.model.ManagementLinks.Companion.parseManagementResponse
import no.skatteetaten.aurora.mokey.model.EndpointType.DISCOVERY
import no.skatteetaten.aurora.mokey.model.EndpointType.HEALTH
import no.skatteetaten.aurora.mokey.model.EndpointType.INFO
import no.skatteetaten.aurora.mokey.model.EndpointType.ENV
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthStatus
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import kotlin.reflect.KClass

@Service
class ManagementInterfaceFactory(val restTemplate: RestTemplate) {
    fun create(host: String?, path: String?): Pair<ManagementInterface?, ManagementEndpointResult<ManagementLinks>> {
        return ManagementInterface.create(restTemplate, host, path)
    }
}

class ManagementEndpoint(val url: String, private val endpointType: EndpointType) {

    val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

    fun <T : Any> findJsonResource(restTemplate: RestTemplate, clazz: KClass<T>): ManagementEndpointResult<T> {
        logger.debug("Getting resource with url={}", url)

        try {
            val responseText: String = try {
                restTemplate.getForObject(url, String::class.java)
            } catch (e: HttpStatusCodeException) {
                if (!e.statusCode.is5xxServerError) throw e
                String(e.responseBodyAsByteArray)
            } ?: "Error while communicating with management endpoint"
            return toManagementEndpointResult(
                    textResponse = responseText,
                    clazz = clazz.java
            )
        } catch (e: Exception) {
            return toManagementEndpointResultAsError(
                    textResponse = "Error while communicating with management endpoint",
                    exception = e
            )
        }
    }

    fun <S : Any> findJsonResource(restTemplate: RestTemplate, parser: (node: JsonNode) -> S): ManagementEndpointResult<S> {
        val intermediate = this.findJsonResource(restTemplate, JsonNode::class)

        if (! intermediate.isSuccess) {
            return toManagementEndpointResultAsError(
                    textResponse = intermediate.textResponse,
                    code = intermediate.code,
                    rootCause = intermediate.rootCause
            )
        }

        return try {
            toManagementEndpointResultAsSuccess(
                    deserialized = parser(intermediate.deserialized!!),
                    textResponse = intermediate.textResponse
            )
        } catch (e: Exception) {
            toManagementEndpointResultAsError(
                    textResponse = "Failed to parse Json",
                    code = "INVALID_FORMAT",
                    rootCause = e.message

            )
        }
    }

    private fun <T : Any> toManagementEndpointResult(deserialized: T?, textResponse: String, code: String, rootCause: String?): ManagementEndpointResult<T> {
        return ManagementEndpointResult(
                deserialized = deserialized,
                textResponse = textResponse,
                code = code,
                rootCause = rootCause,
                endpointType = this.endpointType,
                url = this.url
        )
    }

    private fun <T : Any> toManagementEndpointResultAsSuccess(deserialized: T, textResponse: String): ManagementEndpointResult<T> {
        return toManagementEndpointResult(
                deserialized = deserialized,
                textResponse = textResponse,
                code = "OK",
                rootCause = null)
    }

    private fun <T : Any> toManagementEndpointResultAsError(textResponse: String, code: String, rootCause: String?): ManagementEndpointResult<T> {
        return toManagementEndpointResult(
                deserialized = null,
                textResponse = textResponse,
                code = code,
                rootCause = rootCause)
    }

    private fun <T : Any> toManagementEndpointResultAsError(textResponse: String, exception: Exception): ManagementEndpointResult<T> {
        val errorCode = when (exception) {
            is HttpStatusCodeException -> "ERROR_${exception.statusCode}"
            is RestClientException -> "ERROR_HTTP"
            is JsonParseException -> "INVALID_JSON"
            is MismatchedInputException -> "INVALID_JSON"
            else -> "ERROR_UNKNOWN"
        }

        return toManagementEndpointResult(
                deserialized = null,
                textResponse = textResponse,
                code = errorCode,
                rootCause = exception.message)
    }

    private fun <T : Any> toManagementEndpointResult(textResponse: String, clazz: Class<T>): ManagementEndpointResult<T> {
        return try {
            toManagementEndpointResultAsSuccess(
                    deserialized = jacksonObjectMapper().readValue(textResponse, clazz),
                    textResponse = textResponse
            )
        } catch (e: Exception) {
            toManagementEndpointResultAsError(
                    textResponse = "Failed to parse Json",
                    exception = e
            )
        }
    }
}

class ManagementInterface internal constructor(
    private val restTemplate: RestTemplate,
    val links: ManagementLinks,
    val infoEndpoint: ManagementEndpoint? = null,
    val envEndpoint: ManagementEndpoint? = null,
    val healthEndpoint: ManagementEndpoint? = null

) {
    fun getHealthEndpointResult(): ManagementEndpointResult<HealthResponse> =
            healthEndpoint?.findJsonResource(restTemplate, HealthResponseParser::parse)
                    ?: toManagementEndpointResultLinkMissing(HEALTH)

    fun getInfoEndpointResult(): ManagementEndpointResult<InfoResponse> =
            infoEndpoint?.findJsonResource(restTemplate, InfoResponse::class)
                    ?: toManagementEndpointResultLinkMissing(INFO)

    fun getEnvEndpointResult(): ManagementEndpointResult<JsonNode> =
            envEndpoint?.findJsonResource(restTemplate, JsonNode::class)
                    ?: toManagementEndpointResultLinkMissing(ENV)

    companion object {
        fun create(restTemplate: RestTemplate, host: String?, path: String?): Pair<ManagementInterface?, ManagementEndpointResult<ManagementLinks>> {
            if (host.isNullOrBlank()) {
                return Pair(null, toManagementEndpointResultDiscoveryConfigError("Host address is missing")
                )
            } else if (path.isNullOrBlank()) {
                return Pair(null, toManagementEndpointResultDiscoveryConfigError("Management path is missing")
                )
            }

            // TODO Url composition must be more robust.
            val discoveryUrl = "http://$host$path"
            val discoveryEndpoint = ManagementEndpoint(discoveryUrl, DISCOVERY)
            val response = discoveryEndpoint.findJsonResource(restTemplate, ::parseManagementResponse)

            return response.deserialized?.let { links ->
                Pair(ManagementInterface(
                        restTemplate = restTemplate,
                        links = links,
                        infoEndpoint = links.linkFor(INFO)?.let { url -> ManagementEndpoint(url, INFO) },
                        envEndpoint = links.linkFor(ENV)?.let { url -> ManagementEndpoint(url, ENV) },
                        healthEndpoint = links.linkFor(HEALTH)?.let { url -> ManagementEndpoint(url, HEALTH) }
                ), response)
            } ?: Pair(null, response)
        }

        private fun <T : Any> toManagementEndpointResultLinkMissing(endpointType: EndpointType): ManagementEndpointResult<T> {
            return ManagementEndpointResult(
                    textResponse = "Unable to invoke management endpoint",
                    rootCause = "Unknown endpoint link",
                    endpointType = endpointType,
                    code = "LINK_MISSING"
            )
        }

        private fun <T : Any> toManagementEndpointResultDiscoveryConfigError(cause: String): ManagementEndpointResult<T> {
            return ManagementEndpointResult(
                    textResponse = "Unable to invoke management endpoint",
                    rootCause = cause,
                    endpointType = EndpointType.DISCOVERY,
                    code = "ERROR_CONFIGURATION"
            )
        }
    }
}

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