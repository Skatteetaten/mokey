package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.EndpointType.HEALTH
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.time.Instant

class ManagementEndpointTest : AbstractTest() {

    private val restTemplate = ApplicationConfig().restTemplate(RestTemplateBuilder())
    private val server = MockRestServiceServer.createServer(restTemplate)

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
            ManagementEndpointResult(HealthResponse(
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
            ), json, result.createdAt, HEALTH, "OK", null, healthLink)
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

        managementEndpoint.findJsonResource(restTemplate, InfoResponse::class).deserialized!!.let {
            assert(it.commitId).isEqualTo("5df5258")
            assert(it.commitTime).isEqualTo(Instant.parse("2018-03-23T10:53:31Z"))
            assert(it.buildTime).isEqualTo(Instant.parse("2018-03-23T10:55:40Z"))
        }

        managementEndpoint.findJsonResource(restTemplate, InfoResponse::class).deserialized!!.let {
            assert(it.commitId).isEqualTo("37473fd")
            assert(it.commitTime).isEqualTo(Instant.parse("2018-03-26T11:31:39Z"))
            assert(it.buildTime).isEqualTo(Instant.parse("2018-03-26T11:36:21Z"))
        }
    }

    private fun withJsonFromFile(resourceName: String) =
        withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)

    private fun withJsonString(json: String) = withSuccess(json, MediaType.APPLICATION_JSON)
}
