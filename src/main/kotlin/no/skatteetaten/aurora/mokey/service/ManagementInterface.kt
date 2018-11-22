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
import no.skatteetaten.aurora.mokey.model.HttpResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class ManagementInterfaceFactory(val restTemplate: RestTemplate) {
    fun create(host: String?, path: String?): Pair<ManagementInterface?, ManagementEndpointResult<ManagementLinks>> {
        return ManagementInterface.create(restTemplate, host, path)
    }
}

class ManagementEndpoint(val url: String, private val endpointType: EndpointType) {

    val logger: Logger = LoggerFactory.getLogger(ManagementEndpoint::class.java)

    fun <S : Any> findJsonResource(restTemplate: RestTemplate, clazz: Class<S>): ManagementEndpointResult<S> {
        logger.debug("Getting resource with url={}", url)

        val response = try {
            val response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String::class.java)
            HttpResponse(response.body ?: "", response.statusCodeValue)
        } catch (e: HttpStatusCodeException) {
            val errorResponse = HttpResponse(String(e.responseBodyAsByteArray), e.statusCode.value())
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

    fun <T : Any> findJsonResource(restTemplate: RestTemplate, parser: (node: JsonNode) -> T): ManagementEndpointResult<T> {
        val intermediate = this.findJsonResource(restTemplate, JsonNode::class.java)

        return intermediate.deserialized?.let { deserialized ->
            try {
                toManagementEndpointResultAsSuccess(
                        deserialized = parser(deserialized),
                        response = intermediate.response
                )
            } catch (e: Exception) {
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
        )
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
                errorMessage = exception.message)
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
            infoEndpoint?.findJsonResource(restTemplate, InfoResponse::class.java)
                    ?: toManagementEndpointResultLinkMissing(INFO)

    fun getEnvEndpointResult(): ManagementEndpointResult<JsonNode> =
            envEndpoint?.findJsonResource(restTemplate, JsonNode::class.java)
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
                    errorMessage = "Unknown endpoint link",
                    endpointType = endpointType,
                    resultCode = "LINK_MISSING"
            )
        }

        private fun <T : Any> toManagementEndpointResultDiscoveryConfigError(cause: String): ManagementEndpointResult<T> {
            return ManagementEndpointResult(
                    errorMessage = cause,
                    endpointType = EndpointType.DISCOVERY,
                    resultCode = "ERROR_CONFIGURATION"
            )
        }
    }
}

object HealthResponseParser {

    enum class HealthResponseFormat { SPRING_BOOT_1X, SPRING_BOOT_2X }

    private const val STATUS_PROPERTY = "phase"
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