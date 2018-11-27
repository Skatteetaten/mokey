package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.EndpointType.HEALTH
import no.skatteetaten.aurora.mokey.model.EndpointType.INFO
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.lang.NullPointerException
import java.time.Instant
import java.util.stream.Stream

class ManagementEndpointTest : AbstractTest() {

    private val restTemplate = ApplicationConfig().restTemplate(RestTemplateBuilder())
    private val server = MockRestServiceServer.createServer(restTemplate)
    private val endpointUrl = "http://localhost/path"

    @BeforeEach
    fun resetMocks() {
        server.reset()
    }

    @Test
    fun `should deserialize health endpoint response`() {

        @Language("JSON")
        val json = """{
  "status": "UP",
  "atsServiceHelse": {
    "status": "UP"
  },
  "diskSpace": {
    "status": "UP",
    "total": 10718543872,
    "free": 10508611584,
    "threshold": 10485760
  },
  "db": {
    "status": "UP",
    "database": "Oracle",
    "hello": "Hello"
  }
}"""
        val healthLink = "http://localhost:8081/health"
        server.apply {
            expect(requestTo(healthLink)).andRespond(withJsonString(json))
        }

        val managementEndpoint = ManagementEndpoint(healthLink, HEALTH)

        val result = managementEndpoint.findJsonResource(restTemplate, HealthResponseParser::parse)

        assert(result).isEqualTo(
            ManagementEndpointResult(
                    deserialized = HealthResponse(
                        HealthStatus.UP,
                        mutableMapOf(
                            "atsServiceHelse" to HealthPart(HealthStatus.UP, mutableMapOf()),
                            "diskSpace" to HealthPart(
                                HealthStatus.UP, mutableMapOf(
                                    "total" to LongNode.valueOf(10718543872),
                                    "threshold" to IntNode.valueOf(10485760),
                                    "free" to LongNode.valueOf(10508611584)
                                )
                            ),
                            "db" to HealthPart(
                                HealthStatus.UP, mutableMapOf(
                                    "hello" to TextNode.valueOf("Hello"),
                                    "database" to TextNode.valueOf("Oracle")
                                )
                            )
                        )
                    ),
                    response = HttpResponse(content = json, code = 200),
                    createdAt = result.createdAt,
                    endpointType = HEALTH,
                    resultCode = "OK",
                    errorMessage = null,
                    url = healthLink)
        )
    }

    @Test
    fun `should deserialize info endpoint response`() {

        val infoLink = "http://localhost:8081/info"
        server.apply {
            expect(requestTo(infoLink)).andRespond(withJsonFromFile("info_variant1.json"))
            expect(requestTo(infoLink)).andRespond(withJsonFromFile("info_variant2.json"))
        }

        val managementEndpoint = ManagementEndpoint(infoLink, EndpointType.INFO)

        managementEndpoint.findJsonResource(restTemplate, InfoResponse::class.java).deserialized!!.let {
            assert(it.commitId).isEqualTo("5df5258")
            assert(it.commitTime).isEqualTo(Instant.parse("2018-03-23T10:53:31Z"))
            assert(it.buildTime).isEqualTo(Instant.parse("2018-03-23T10:55:40Z"))
        }

        managementEndpoint.findJsonResource(restTemplate, InfoResponse::class.java).deserialized!!.let {
            assert(it.commitId).isEqualTo("37473fd")
            assert(it.commitTime).isEqualTo(Instant.parse("2018-03-26T11:31:39Z"))
            assert(it.buildTime).isEqualTo(Instant.parse("2018-03-26T11:36:21Z"))
        }
    }

    @ParameterizedTest(name = "Fail correctly on {0}")
    @ArgumentsSource(ErrorCases::class)
    fun `Fail on error responses`(
        description: String,
        response: String,
        mediaType: MediaType,
        errorCode: String?,
        responseCode: HttpStatus
    ) {
        val endpoint = ManagementEndpoint(url = endpointUrl, endpointType = HEALTH)
        server.expect(ExpectedCount.once(), requestTo(endpointUrl))
                .andRespond(MockRestResponseCreators.withStatus(responseCode).body(response).contentType(mediaType))

        val result = endpoint.findJsonResource(restTemplate, { throw NullPointerException() })

        assert(result.isSuccess).isFalse()
        assert(result.endpointType).isEqualTo(EndpointType.HEALTH)
        assert(result.response!!.code).isEqualTo(responseCode.value())
        assert(result.response!!.content).isEqualTo(response)
        assert(result.resultCode).isEqualTo(errorCode)
        assert(result.createdAt).isNotNull()
    }

    @ParameterizedTest(name = "Handle {0} correctly")
    @ArgumentsSource(SuccessCases::class)
    fun `Handle missing data correctly`(
        description: String,
        response: String,
        mediaType: MediaType,
        responseCode: HttpStatus
    ) {

        val endpoint = ManagementEndpoint(url = endpointUrl, endpointType = HEALTH)
        server.expect(ExpectedCount.once(), requestTo(endpointUrl))
                .andRespond(MockRestResponseCreators.withStatus(responseCode).body(response).contentType(mediaType))

        val result = endpoint.findJsonResource(restTemplate, { HealthResponse(HealthStatus.UP) })

        assert(result.isSuccess).isTrue()
        assert(result.endpointType).isEqualTo(EndpointType.HEALTH)
        assert(result.response!!.code).isEqualTo(responseCode.value())
        assert(result.response!!.content).isEqualTo(response)
        assert(result.resultCode).isEqualTo("OK")
        assert(result.createdAt).isNotNull()
    }

    class ErrorCases : ArgumentsProvider {
        fun args(description: String, response: String, mediaType: MediaType, errorCode: String, responseCode: HttpStatus = HttpStatus.OK) =
                Arguments.of(description, response, mediaType, errorCode, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
                args("Empty response", "", MediaType.APPLICATION_JSON, "INVALID_JSON"),
                args("Parser error", "{ \"status\": \"observe\"}", MediaType.APPLICATION_JSON, "INVALID_FORMAT"),
                args("Invalid Json", "{ status: {}}", MediaType.APPLICATION_JSON, "INVALID_JSON"),
                args("404", "", MediaType.APPLICATION_JSON, "ERROR_404", HttpStatus.NOT_FOUND),
                args("Invalid content / 200", "<html><body><h1>hello</h1></body></html>", MediaType.TEXT_HTML, "INVALID_JSON"),
                args("Invalid content / 500", "<html><body><h1>hello</h1></body></html>", MediaType.TEXT_HTML, "INVALID_JSON", HttpStatus.INTERNAL_SERVER_ERROR),
                args("Invalid content / 500", "<html><body><h1>hello</h1></body></html>", MediaType.APPLICATION_JSON, "INVALID_JSON", HttpStatus.INTERNAL_SERVER_ERROR)
        )
    }

    class SuccessCases : ArgumentsProvider {
        fun args(description: String, response: String, mediaType: MediaType, responseCode: HttpStatus = HttpStatus.OK) =
                Arguments.of(description, response, mediaType, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
                args("Empty Json", "{}", MediaType.APPLICATION_JSON),
                args("Minimal Json / 200", "{ \"status\": {}}", MediaType.APPLICATION_JSON),
                args("Empty Json / 500", "{}", MediaType.APPLICATION_JSON, HttpStatus.INTERNAL_SERVER_ERROR)
        )
    }

    private fun withJsonFromFile(resourceName: String) =
        withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)

    private fun withJsonString(json: String) = withSuccess(json, MediaType.APPLICATION_JSON)
}
