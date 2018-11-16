package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.fail
import com.jayway.jsonpath.JsonPath
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers
import org.springframework.test.web.client.response.MockRestResponseCreators

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
        assert(p.second.errorMessage).isEqualTo("Host address is missing")
    }

    @Test
    fun `Fail to create management interface when path is empty`() {
        val p = ManagementInterface.create(restTemplate, "localhost:8081", "")
        assert(p.first).isNull()
        assert(p.second.errorMessage).isEqualTo("Management path is missing")
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

    private fun withJsonFromFile(resourceName: String) =
            MockRestResponseCreators.withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)
}