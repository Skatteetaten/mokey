package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
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
    private val imageRegistryClient = ImageRegistryClient(webClient)
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
            assertThat(response.success).isTrue()
            assertThat(response.items).isNotEmpty()

            val resource = response.items[0]
            assertThat(resource).isNotNull()
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
