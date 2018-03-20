package no.skatteetaten.aurora.mokey.service

import com.jayway.jsonpath.JsonPath
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.service.Endpoint.HEALTH
import no.skatteetaten.aurora.mokey.service.Endpoint.MANAGEMENT
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.client.ExpectedCount.once
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class ManagementEndpointTest : AbstractTest() {

    val restTemplate = ApplicationConfig().restTemplate(RestTemplateBuilder())
    val server = MockRestServiceServer.createServer(restTemplate)

    val managementUrl = "http://localhost:8081/management"

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

    @TestFactory
    fun `should handle management link response errors`(): Collection<DynamicTest> {

        data class TestData(
                val response: String,
                val mediaType: MediaType,
                val endpoint: Endpoint,
                val errorCode: String)

        return listOf(
                TestData("{}", APPLICATION_JSON, HEALTH, "LINK_MISSING"),
                TestData("{ \"_links\": {}}", APPLICATION_JSON, HEALTH, "LINK_MISSING"),
                TestData("{ \"_links\": { \"health\": null}}", APPLICATION_JSON, MANAGEMENT, "INVALID_FORMAT"),
                TestData("", APPLICATION_JSON, MANAGEMENT, "INVALID_FORMAT")
        ).map {
            server.expect(once(), requestTo(managementUrl)).andRespond(withSuccess(it.response, it.mediaType))

            dynamicTest("${it.response} as ${it.mediaType} yields ${it.errorCode} for ${it.endpoint} endpoint") {

                val e = assertThrows(ManagementEndpointException::class.java) {
                    ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse()
                }
                assertThat(e.endpoint).isEqualTo(it.endpoint)
                assertThat(e.errorCode).isEqualTo(it.errorCode)
            }
        }

//        server.apply {
/*
            expect(once(), requestTo(managementUrl)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON))
            expect(once(), requestTo(managementUrl)).andRespond(withSuccess("{ \"_links\": {}}", MediaType.APPLICATION_JSON))
            expect(once(), requestTo(managementUrl)).andRespond(withSuccess("{ \"_links\": { \"health\": null}}", MediaType.APPLICATION_JSON))

            expect(once(), requestTo(managementUrl)).andRespond(withSuccess("", MediaType.APPLICATION_JSON))
            expect(once(), requestTo(managementUrl)).andRespond(withSuccess("{ _links: {}}", MediaType.APPLICATION_JSON))

            expect(once(), requestTo(managementUrl)).andRespond(withServerError().body("{ _links: {}}").contentType(MediaType.APPLICATION_JSON))
*/
//            expect(once(), requestTo(managementUrl)).andRespond(withSuccess().body("<html><body><h1>hello</h1></body></html>").contentType(MediaType.TEXT_HTML))
//            expect(once(), requestTo(managementUrl)).andRespond(withServerError().body("<html><body><h1>hello</h1></body></html>").contentType(MediaType.TEXT_HTML))
//            expect(once(), requestTo(managementUrl)).andRespond(withStatus(HttpStatus.NOT_FOUND))
//        }

/*
        try {
            ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse().also { fail("Should fail") }
        } catch (e: ManagementEndpointException) {
            assertThat(e.endpoint).isEqualTo(Endpoint.HEALTH)
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }

        try {
            ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse().also { fail("Should fail") }
        } catch (e: ManagementEndpointException) {
            assertThat(e.endpoint).isEqualTo(Endpoint.HEALTH)
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }

        try {
            ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse().also { fail("Should fail") }
        } catch (e: ManagementEndpointException) {
            assertThat(e.endpoint).isEqualTo(Endpoint.HEALTH)
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }

        try {
            ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse().also { fail("Should fail") }
        } catch (e: ManagementEndpointException) {
            e.printStackTrace()
            assertThat(e.endpoint).isEqualTo(Endpoint.MANAGEMENT)
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }

        try {
            ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse().also { fail("Should fail") }
        } catch (e: ManagementEndpointException) {
            e.printStackTrace()
            assertThat(e.endpoint).isEqualTo(Endpoint.MANAGEMENT)
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }
*/

/*
        try {
            ManagementEndpoint.create(restTemplate, managementUrl).getHealthEndpointResponse().also { fail("Should fail") }
        } catch (e: ManagementEndpointException) {
            e.printStackTrace()
            assertThat(e.endpoint).isEqualTo(Endpoint.MANAGEMENT)
            assertThat(e.errorCode).isEqualTo("LINK_MISSING")
        }
*/
    }

    private fun withJsonResponse(resourceName: String) = withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)
}
