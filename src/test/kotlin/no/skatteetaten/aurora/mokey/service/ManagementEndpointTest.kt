package no.skatteetaten.aurora.mokey.service

import com.jayway.jsonpath.JsonPath
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.service.Endpoint.MANAGEMENT
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.http.HttpStatus.*
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.TEXT_HTML
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
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
    fun `should use links from management response for health and info endpoints`() {

        val resource = loadResource("management.json")
        val (healthLink, infoLink) = JsonPath.parse(resource).let { json ->
            listOf("health", "info").map { json.read("$._links.$it.href", String::class.java) }
        }

        server.apply {
            expect(requestTo(managementUrl)).andRespond(withSuccess(resource, APPLICATION_JSON))
            expect(requestTo(healthLink)).andRespond(withJsonResponse("health.json"))
            expect(requestTo(infoLink)).andRespond(withJsonResponse("info.json"))
        }

        val managementEndpoint = ManagementEndpoint.create(restTemplate, managementUrl)
        managementEndpoint.getHealthEndpointResponse()
        managementEndpoint.getInfoEndpointResponse()
    }

    @ParameterizedTest
    @ArgumentsSource(TestDataProvider::class)
    fun `should handle management link responses`(it: TestData) {

        server.expect(once(), requestTo(managementUrl)).andRespond(withStatus(it.responseCode).body(it.response).contentType(it.mediaType))

        val create: () -> Unit = { ManagementEndpoint.create(restTemplate, managementUrl) }
        if (it.errorCode != null) {
            val e = assertThrows(ManagementEndpointException::class.java, create)
            assertThat(e.endpoint).isEqualTo(MANAGEMENT)
            assertThat(e.errorCode).isEqualTo(it.errorCode)
        } else {
            create.invoke()
        }
    }

    data class TestData(val response: String, val mediaType: MediaType, val errorCode: String? = null, val responseCode: HttpStatus = OK)

    class TestDataProvider : ArgumentsProvider {
        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
                TestData("{}", APPLICATION_JSON),
                TestData("{ \"_links\": {}}", APPLICATION_JSON),
                TestData("", APPLICATION_JSON),
                TestData("{}", APPLICATION_JSON, responseCode = INTERNAL_SERVER_ERROR),
                TestData("{ \"_links\": { \"health\": null}}", APPLICATION_JSON, "INVALID_FORMAT"),
                TestData("{ _links: {}}", APPLICATION_JSON, "INVALID_JSON"),
                TestData("", APPLICATION_JSON, "ERROR_404", NOT_FOUND),
                TestData("<html><body><h1>hello</h1></body></html>", TEXT_HTML, "INVALID_JSON"),
                TestData("<html><body><h1>hello</h1></body></html>", TEXT_HTML, "INVALID_JSON", INTERNAL_SERVER_ERROR),
                TestData("<html><body><h1>hello</h1></body></html>", APPLICATION_JSON, "INVALID_JSON", INTERNAL_SERVER_ERROR)
        ).map { Arguments.of(it) }
    }

    private fun withJsonResponse(resourceName: String) = withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)
}
