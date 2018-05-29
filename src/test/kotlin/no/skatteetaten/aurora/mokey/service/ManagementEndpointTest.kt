package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import com.jayway.jsonpath.JsonPath
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.Endpoint.MANAGEMENT
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.TEXT_HTML
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Instant
import java.util.stream.Stream

class ManagementEndpointTest : AbstractTest() {

    val restTemplate = ApplicationConfig().restTemplate(RestTemplateBuilder())
    val server = MockRestServiceServer.createServer(restTemplate)

    val managementUrl = "http://localhost:8081/management"

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

        val managementEndpoint =
            ManagementEndpoint(restTemplate, ManagementLinks(mapOf(Endpoint.HEALTH.key to healthLink)))
        val response = managementEndpoint.getHealthEndpointResponse()

        assert(response).isEqualTo(
            HealthResponse(
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
            )
        )
    }

    @Test
    fun `should deserialize info endpoint response`() {

        val infoLink = "http://localhost:8081/info"
        server.apply {
            expect(requestTo(infoLink)).andRespond(withJsonFromFile("info_variant1.json"))
            expect(requestTo(infoLink)).andRespond(withJsonFromFile("info_variant2.json"))
        }

        val managementEndpoint = ManagementEndpoint(restTemplate, ManagementLinks(mapOf(Endpoint.INFO.key to infoLink)))

        managementEndpoint.getInfoEndpointResponse().let {
            assert(it.commitId).isEqualTo("5df5258")
            assert(it.commitTime).isEqualTo(Instant.parse("2018-03-23T10:53:31Z"))
            assert(it.buildTime).isEqualTo(Instant.parse("2018-03-23T10:55:40Z"))
        }
        managementEndpoint.getInfoEndpointResponse().let {
            assert(it.commitId).isEqualTo("37473fd")
            assert(it.commitTime).isEqualTo(Instant.parse("2018-03-26T11:31:39Z"))
            assert(it.buildTime).isEqualTo(Instant.parse("2018-03-26T11:36:21Z"))
        }
    }

    @Test
    fun `should use links from management response for health and info endpoints`() {

        val resource = loadResource("management.json")
        val (healthLink, infoLink) = JsonPath.parse(resource).let { json ->
            listOf("health", "info").map { json.read("$._links.$it.href", String::class.java) }
        }

        server.apply {
            expect(requestTo(managementUrl)).andRespond(withSuccess(resource, APPLICATION_JSON))
            expect(requestTo(healthLink)).andRespond(withJsonFromFile("health.json"))
            expect(requestTo(infoLink)).andRespond(withJsonFromFile("info.json"))
        }

        val managementEndpoint = ManagementEndpoint.create(restTemplate, managementUrl)
        managementEndpoint.getHealthEndpointResponse()
        managementEndpoint.getInfoEndpointResponse()
    }

    @ParameterizedTest(name = "{0} as {1} yields {2} for management endpoint")
    @ArgumentsSource(ErrorCases::class)
    fun `should fail on management error responses`(
        response: String,
        mediaType: MediaType,
        errorCode: String?,
        responseCode: HttpStatus
    ) {

        server.expect(once(), requestTo(managementUrl))
            .andRespond(withStatus(responseCode).body(response).contentType(mediaType))

        val e = assertThrows(ManagementEndpointException::class.java) {
            ManagementEndpoint.create(restTemplate, managementUrl)
        }
        assertThat(e.endpoint).isEqualTo(MANAGEMENT)
        assertThat(e.errorCode).isEqualTo(errorCode)
    }

    @ParameterizedTest(name = "{0} as {1} is handled without error")
    @ArgumentsSource(SuccessCases::class)
    fun `should handle management link responses with missing data`(
        response: String,
        mediaType: MediaType,
        responseCode: HttpStatus
    ) {

        server.expect(once(), requestTo(managementUrl))
            .andRespond(withStatus(responseCode).body(response).contentType(mediaType))

        val managementEndpoint = ManagementEndpoint.create(restTemplate, managementUrl)
        listOf(
            { managementEndpoint.getHealthEndpointResponse() },
            { managementEndpoint.getInfoEndpointResponse() }
        ).forEach {
            val e = assertThrows(ManagementEndpointException::class.java, { it.invoke() })
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }
    }

    class SuccessCases : ArgumentsProvider {
        fun args(response: String, mediaType: MediaType, responseCode: HttpStatus = OK) =
            Arguments.of(response, mediaType, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            args("{}", APPLICATION_JSON),
            args("{ \"_links\": {}}", APPLICATION_JSON),
            args("{}", APPLICATION_JSON, INTERNAL_SERVER_ERROR)
        )
    }

    class ErrorCases : ArgumentsProvider {
        fun args(response: String, mediaType: MediaType, errorCode: String, responseCode: HttpStatus = OK) =
            Arguments.of(response, mediaType, errorCode, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            args("", APPLICATION_JSON, "INVALID_JSON"),
            args("{ \"_links\": { \"health\": null}}", APPLICATION_JSON, "INVALID_FORMAT"),
            args("{ _links: {}}", APPLICATION_JSON, "INVALID_JSON"),
            args("", APPLICATION_JSON, "ERROR_404", NOT_FOUND),
            args("<html><body><h1>hello</h1></body></html>", TEXT_HTML, "INVALID_JSON"),
            args("<html><body><h1>hello</h1></body></html>", TEXT_HTML, "INVALID_JSON", INTERNAL_SERVER_ERROR),
            args("<html><body><h1>hello</h1></body></html>", APPLICATION_JSON, "INVALID_JSON", INTERNAL_SERVER_ERROR)
        )
    }

    private fun withJsonFromFile(resourceName: String) =
        withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)

    private fun withJsonString(json: String) = withSuccess(json, MediaType.APPLICATION_JSON)
}
