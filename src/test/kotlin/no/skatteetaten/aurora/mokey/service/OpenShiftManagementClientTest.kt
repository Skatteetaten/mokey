package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.url
import no.skatteetaten.aurora.mokey.PodDataBuilder
import no.skatteetaten.aurora.mokey.model.EndpointType.HEALTH
import no.skatteetaten.aurora.mokey.model.EndpointType.INFO
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementEndpoint
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import java.io.File
import java.time.Instant.parse
import java.util.stream.Stream

class OpenShiftManagementClientTest {
    private val server = MockWebServer()
    private val client = OpenShiftManagementClient(
        client = KubernetesReactorClient(server.url, "test-token"),
        cacheManagement = false
    )

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
        }
        """.trimIndent()

        server.execute(json) {
            runBlocking {
                val managementEndpoint = ManagementEndpoint(
                    PodDataBuilder().build(),
                    8080,
                    "test",
                    HEALTH
                )
                val resource = client.findJsonResource<JsonNode>(managementEndpoint)

                assertThat(resource.endpointType).isEqualTo(HEALTH)
                assertThat(resource.resultCode).isEqualTo("OK")
            }
        }
    }

    @Test
    fun `should deserialize info endpoint response, variant1`() {
        val json = loadJson("info_variant1.json")

        server.execute(json) {
            val managementEndpoint = ManagementEndpoint(
                PodDataBuilder().build(),
                8080,
                "test",
                INFO
            )

            runBlocking {
                val response = client.findJsonResource<InfoResponse>(managementEndpoint)
                val infoResponse = response.deserialized!!

                assertThat(infoResponse.commitId).isEqualTo("5df5258")
                assertThat(infoResponse.commitTime).isEqualTo(parse("2018-03-23T10:53:31Z"))
                assertThat(infoResponse.buildTime).isEqualTo(parse("2018-03-23T10:55:40Z"))
            }
        }
    }

    @Test
    fun `should deserialize info endpoint response, variant2`() {
        val json = loadJson("info_variant2.json")

        server.execute(json) {
            val managementEndpoint = ManagementEndpoint(
                PodDataBuilder().build(),
                8080,
                "test",
                INFO
            )

            runBlocking {
                val response = client.findJsonResource<InfoResponse>(managementEndpoint)
                val infoResponse = response.deserialized!!

                assertThat(infoResponse.commitId).isEqualTo("37473fd")
                assertThat(infoResponse.commitTime).isEqualTo(parse("2018-03-26T11:31:39Z"))
                assertThat(infoResponse.buildTime).isEqualTo(parse("2018-03-26T11:36:21Z"))
            }
        }
    }

    @ParameterizedTest(name = "Fail correctly on {0}")
    @ArgumentsSource(ErrorCases::class)
    fun `Fail on error responses`(
        description: String,
        response: String,
        mediaType: MediaType,
        responseCode: HttpStatus
    ) {
        val managementEndpoint = ManagementEndpoint(
            PodDataBuilder().build(),
            8080,
            "test",
            HEALTH
        )
        val mockResponse = MockResponse().apply {
            setResponseCode(responseCode.value())
            setBody(response)
            addHeader(HttpHeaders.CONTENT_TYPE, mediaType)
        }

        server.execute(mockResponse) {
            runBlocking {
                val resource = client.findJsonResource<JsonNode>(managementEndpoint).healthResult()

                assertThat(resource.isSuccess).isFalse()
                assertThat(resource.endpointType).isEqualTo(HEALTH)
                assertThat(resource.resultCode).isNotNull()
                assertThat(resource.errorMessage).isNotNull()
                assertThat(resource.createdAt).isNotNull()
            }
        }
    }

    @ParameterizedTest(name = "Handles {0} correctly")
    @EnumSource(value = HealthStatus::class)
    fun `Handle health status`(healthStatus: HealthStatus) {
        val managementEndpoint = ManagementEndpoint(
            PodDataBuilder().build(),
            8080,
            "test",
            HEALTH
        )
        val mockResponse = jsonResponse("""{ "status": "${healthStatus.name}" }""")

        server.execute(mockResponse) {
            runBlocking {
                val resource = client.findJsonResource<JsonNode>(managementEndpoint).healthResult()

                assertThat(resource.isSuccess).isTrue()
                assertThat(resource.endpointType).isEqualTo(HEALTH)
                assertThat(resource.resultCode).isEqualTo("OK")
                assertThat(resource.errorMessage).isNull()
                assertThat(resource.createdAt).isNotNull()
            }
        }
    }

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun loadJson(fileName: String): String {
        val folder = this::class.java.getResource("${this::class.simpleName}/$fileName")

        return File(folder.toURI()).readText()
    }

    class ErrorCases : ArgumentsProvider {
        private fun args(
            description: String,
            response: String,
            mediaType: MediaType,
            responseCode: HttpStatus = OK,
        ): Arguments = Arguments.of(description, response, mediaType, responseCode)

        override fun provideArguments(context: ExtensionContext?): Stream<out Arguments> = Stream.of(
            args("Empty response", "", MediaType.APPLICATION_JSON),
            args("Parser error", "{ \"status\": \"observe\"}", MediaType.APPLICATION_JSON),
            args("Invalid Json", "{ status: {}}", MediaType.APPLICATION_JSON),
            args("404", "", MediaType.APPLICATION_JSON, HttpStatus.NOT_FOUND),
            args(
                "Invalid content / 200",
                "<html><body><h1>hello</h1></body></html>",
                MediaType.TEXT_HTML
            ),
            args(
                "Invalid content / 500",
                "<html><body><h1>hello</h1></body></html>",
                MediaType.TEXT_HTML,
                HttpStatus.INTERNAL_SERVER_ERROR
            ),
            args(
                "Invalid content / 500",
                "<html><body><h1>hello</h1></body></html>",
                MediaType.APPLICATION_JSON,
                HttpStatus.INTERNAL_SERVER_ERROR
            ),
        )
    }
}
