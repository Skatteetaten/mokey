package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.fail
import com.jayway.jsonpath.JsonPath
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.model.EndpointType
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
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators
import java.util.stream.Stream

class ManagementInterfaceTest : AbstractTest() {

    private val restTemplate = ApplicationConfig().restTemplate(RestTemplateBuilder())
    private val server = MockRestServiceServer.createServer(restTemplate)

    val discoveryHost = "localhost:8081"
    val discoveryPath = "/management"
    val discoveryLink = "http://$discoveryHost$discoveryPath"

    @BeforeEach
    fun resetMocks() {
        server.reset()
    }

    @Test
    fun `Fail to create management interface when host address is empty`() {
        val p = ManagementInterface.create(restTemplate, "", "/test")
        assert(p.first).isNull()
        assert(p.second.rootCause).isEqualTo("Host address is missing")
    }

    @Test
    fun `Fail to create management interface when path is empty`() {
        val p = ManagementInterface.create(restTemplate, "localhost:8081", "")
        assert(p.first).isNull()
        assert(p.second.rootCause).isEqualTo("Management path is missing")
    }

    @Test
    fun `should use links from management response for health and info endpoints`() {

        val resource = loadResource("management.json")

        val (healthLink, infoLink) = JsonPath.parse(resource).let { json ->
            listOf("health", "info").map { json.read("$._links.$it.href", String::class.java) }
        }

        server.apply {
            expect(MockRestRequestMatchers.requestTo(discoveryLink)).andRespond(MockRestResponseCreators.withSuccess(resource, MediaType.APPLICATION_JSON))
            expect(MockRestRequestMatchers.requestTo(healthLink)).andRespond(withJsonFromFile("health.json"))
            expect(MockRestRequestMatchers.requestTo(infoLink)).andRespond(withJsonFromFile("info.json"))
        }

        val p = ManagementInterface.create(restTemplate, discoveryHost, discoveryPath)

        p.first?.let { managementInterface ->
            managementInterface.getHealthEndpointResult()
            managementInterface.getInfoEndpointResult()
        } ?: fail("Management inteface is null")
    }

    @ParameterizedTest(name = "{0} as {1} yields {2} for management endpoint")
    @ArgumentsSource(ErrorCases::class)
    fun `should fail on discovery error responses`(
        response: String,
        mediaType: MediaType,
        errorCode: String?,
        responseCode: HttpStatus
    ) {

        server.expect(ExpectedCount.once(), requestTo(discoveryLink))
                .andRespond(MockRestResponseCreators.withStatus(responseCode).body(response).contentType(mediaType))

        val p = ManagementInterface.create(restTemplate, discoveryHost, discoveryPath)

        p.first?.let { managementInterface ->
            fail("Expected management interface to be null")
        }

        p.second.let { discoveryEndpointResult ->
            assert(discoveryEndpointResult.isSuccess).isFalse()
            assert(discoveryEndpointResult.endpointType).isEqualTo(EndpointType.DISCOVERY)
            assert(discoveryEndpointResult.code).isEqualTo(errorCode)
        }
    }

    @ParameterizedTest(name = "{0} as {1} is handled without error")
    @ArgumentsSource(SuccessCases::class)
    fun `should handle management link responses with missing data`(
        response: String,
        mediaType: MediaType,
        responseCode: HttpStatus
    ) {

        server.expect(ExpectedCount.once(), requestTo(discoveryLink))
                .andRespond(MockRestResponseCreators.withStatus(responseCode).body(response).contentType(mediaType))

        val p = ManagementInterface.create(restTemplate, discoveryHost, discoveryPath)

        p.first?.let { managementInterface ->
            listOf(
                    { managementInterface.getHealthEndpointResult() },
                    { managementInterface.getInfoEndpointResult() }
            ).forEach {
                assert(it.invoke().code).isEqualTo("LINK_MISSING")
            }
        } ?: fail("Management interface is null")
    }

    class SuccessCases : ArgumentsProvider {
        fun args(response: String, mediaType: MediaType, responseCode: HttpStatus = HttpStatus.OK) =
                Arguments.of(response, mediaType, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
                args("{}", MediaType.APPLICATION_JSON),
                args("{ \"_links\": {}}", MediaType.APPLICATION_JSON),
                args("{}", MediaType.APPLICATION_JSON, HttpStatus.INTERNAL_SERVER_ERROR)
        )
    }

    class ErrorCases : ArgumentsProvider {
        fun args(response: String, mediaType: MediaType, errorCode: String, responseCode: HttpStatus = HttpStatus.OK) =
                Arguments.of(response, mediaType, errorCode, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
                args("", MediaType.APPLICATION_JSON, "INVALID_JSON"),
                args("{ \"_links\": { \"health\": null}}", MediaType.APPLICATION_JSON, "INVALID_FORMAT"),
                args("{ _links: {}}", MediaType.APPLICATION_JSON, "INVALID_JSON"),
                args("", MediaType.APPLICATION_JSON, "ERROR_404", HttpStatus.NOT_FOUND),
                args("<html><body><h1>hello</h1></body></html>", MediaType.TEXT_HTML, "INVALID_JSON"),
                args("<html><body><h1>hello</h1></body></html>", MediaType.TEXT_HTML, "INVALID_JSON", HttpStatus.INTERNAL_SERVER_ERROR),
                args("<html><body><h1>hello</h1></body></html>", MediaType.APPLICATION_JSON, "INVALID_JSON", HttpStatus.INTERNAL_SERVER_ERROR)
        )
    }

    private fun withJsonFromFile(resourceName: String) =
            MockRestResponseCreators.withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)
}