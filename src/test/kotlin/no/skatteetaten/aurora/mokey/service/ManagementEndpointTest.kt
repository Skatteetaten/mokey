package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import com.jayway.jsonpath.JsonPath
import no.skatteetaten.aurora.mokey.AbstractTest
import no.skatteetaten.aurora.mokey.ApplicationConfig
import no.skatteetaten.aurora.mokey.service.Endpoint.MANAGEMENT
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

        val managementEndpoint = ManagementEndpoint(restTemplate, ManagementLinks(mapOf(Endpoint.HEALTH.key to healthLink)))
        val response = managementEndpoint.getHealthEndpointResponse()

        assert(response).isEqualTo(HealthResponse(
                HealthStatus.UP,
                mutableMapOf(
                        "atsServiceHelse" to HealthPart(HealthStatus.UP, mutableMapOf()),
                        "diskSpace" to HealthPart(HealthStatus.UP, mutableMapOf(
                                "total" to LongNode.valueOf(10718543872),
                                "threshold" to IntNode.valueOf(10485760),
                                "free" to LongNode.valueOf(10508611584)
                        )),
                        "db" to HealthPart(HealthStatus.UP, mutableMapOf(
                                "hello" to TextNode.valueOf("Hello"),
                                "database" to TextNode.valueOf("Oracle")
                        ))
                )
        ))
    }

    @Test
    fun `should deserialize info endpoint response`() {

        @Language("JSON")
        val json = """{
  "serviceLinks": {
    "metrics": "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}"
  },
  "podLinks": {
    "metrics": "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}&var-instance={podName}"
  },
  "dependencies": {},
  "build": {
    "name": "skattemelding-core",
    "artifactid": "skattemelding-core",
    "time": "${'$'}{timestamp}",
    "version": "bugfix_feed_sikkerhet-SNAPSHOT",
    "group": "ske.fastsetting.formueinntekt.skattemelding"
  },
  "git": {
    "commit.message.short": "SH-49: registrerer lagreresource.",
    "commit.user.name": "Naimdjon Takhirov",
    "commit.id.describe-short": "v1.0.362-1",
    "branch": "37473fd288228a09687d97f2650da5a7fde70d0a",
    "commit.id.abbrev": "37473fd",
    "build.time": "26.03.2018 @ 13:36:21 CEST",
    "commit.time": "26.03.2018 @ 13:31:39 CEST",
    "commit.id": "37473fd288228a09687d97f2650da5a7fde70d0a",
    "tags": "",
    "commit.message.full": "SH-49: registrerer lagreresource.\n",
    "commit.id.describe": "v1.0.362-1-g37473fd",
    "build.user.name": "Jenkins",
    "remote.origin.url": "https://git.aurora.skead.no/scm/SIR/skattemelding-core.git",
    "commit.user.email": "naimdjon.takhirov@skatteetaten.no",
    "build.user.email": "nobody@skatteetaten.no"
  }
}"""
        @Language("JSON")
        val json2 = """{
  "application": {
    "version": "0.0.144"
  },
  "serviceLinks": {
    "metrics": "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}",
    "api-doc": "mss-backend-sirius-norveig-sh-240.utv.paas.skead.no/docs/index.html"
  },
  "podLinks": {
    "metrics": "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}&var-instance={podName}"
  },
  "dependencies": {
    "innbetaltskatt": "http://innbetaltskatt",
    "partsregister": "http://part-identitet-part-fk1-utv.utv.paas.skead.no",
    "skattemeldingcore": "http://skattemelding-core",
    "sts": "http://int-utv.skead.no:11100/felles/sikkerhet/stsSikkerhet/v2"
  },
  "imageBuildTime": "2018-03-23T10:57:38Z",
  "auroraVersion": "0.0.144-b1.8.0-flange-8.152.18",
  "git": {
    "commit": {
      "time": "2018-03-23T10:53:31Z",
      "id": "5df5258"
    },
    "branch": "5df5258db4c5e6077b26e41d2daeed532f08841a"
  },
  "build": {
    "version": "0.0.144",
    "artifact": "minskatteside-skattemelding-backend",
    "name": "minskatteside-skattemelding-backend",
    "group": "ske.fastsetting.formueinntekt.skattemelding.kjerne",
    "time": "2018-03-23T10:55:40Z"
  }
}"""
        val infoLink = "http://localhost:8081/info"
        server.apply {
            expect(requestTo(infoLink)).andRespond(withJsonString(json))
            expect(requestTo(infoLink)).andRespond(withJsonString(json2))
        }

        val managementEndpoint = ManagementEndpoint(restTemplate, ManagementLinks(mapOf(Endpoint.INFO.key to infoLink)))
        val response = managementEndpoint.getInfoEndpointResponse()

        println(response)
        println(response.commitId)
        println(response.commitTime)
        println(response.buildTime)

        val response2 = managementEndpoint.getInfoEndpointResponse()

        println(response2)
        println(response2.commitId)
        println(response2.commitTime)
        println(response2.buildTime)
//        assert(response).isEqualTo(InfoResponse(buildTime = null))
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
    fun `should fail on management error responses`(response: String, mediaType: MediaType, errorCode: String?, responseCode: HttpStatus) {

        server.expect(once(), requestTo(managementUrl)).andRespond(withStatus(responseCode).body(response).contentType(mediaType))

        val e = assertThrows(ManagementEndpointException::class.java) {
            ManagementEndpoint.create(restTemplate, managementUrl)
        }
        assertThat(e.endpoint).isEqualTo(MANAGEMENT)
        assertThat(e.errorCode).isEqualTo(errorCode)
    }

    @ParameterizedTest(name = "{0} as {1} is handled without error")
    @ArgumentsSource(SuccessCases::class)
    fun `should handle management link responses with missing data`(response: String, mediaType: MediaType, responseCode: HttpStatus) {

        server.expect(once(), requestTo(managementUrl)).andRespond(withStatus(responseCode).body(response).contentType(mediaType))

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

    private fun withJsonFromFile(resourceName: String) = withSuccess(loadResource(resourceName), MediaType.APPLICATION_JSON)
    private fun withJsonString(json: String) = withSuccess(json, MediaType.APPLICATION_JSON)
}
