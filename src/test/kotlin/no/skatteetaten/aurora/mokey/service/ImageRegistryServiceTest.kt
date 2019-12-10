package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ImageRegistryServiceTest {
    private val server = MockWebServer()
    private val webClient = WebClient.create(server.url("/").toString())
    private val imageRegistryClient =
        ImageRegistryClient(webClient, jacksonObjectMapper().registerModule(JavaTimeModule()))
    private val imageRegistryService = ImageRegistryService(imageRegistryClient)
    private val tagUrls = listOf("localhost/sha256:1", "localhost/sha256:2")

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `find tags by name`() {
        val manifest = ClassPathResource("manifest.json").file.readText()
        server.execute(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(manifest)
        ) {
            val response = imageRegistryService.findTagsByName(tagUrls)
            assertThat(response).isNotNull()
        }
    }

    @Test
    fun `find tags by name, response contains failure`() {
        server.execute(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(
                    jacksonObjectMapper().writeValueAsString(
                        AuroraResponse<ImageTagResource>(
                            success = false,
                            message = "test failure"
                        )
                    )
                )
        ) {
            assertThat {
                imageRegistryService.findTagsByName(tagUrls)
            }.isFailure().cause().isNotNull().isInstanceOf(ServiceException::class)
        }
    }

    @Test
    fun `throws IllegalStateException if response is 404`() {
        server.execute(MockResponse().setResponseCode(404)) {
            assertThat {
                imageRegistryService.findTagsByName(tagUrls)
            }.isFailure().isInstanceOf(IllegalStateException::class)
        }
    }
}
