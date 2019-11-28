package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient

internal class ImageRegistryServiceTest {
    private val server = MockWebServer()
    private val webClient = WebClient.create(server.url("/").toString())
    private val imageRegistryClient = ImageRegistryClient(webClient)
    private val imageRegistryService = ImageRegistryService(imageRegistryClient)

    @BeforeEach
    internal fun setUp() {
        val manifest = ClassPathResource("manifest.json").file.readText()
        server.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(manifest)
        )
    }

    @AfterEach
    internal fun tearDown() {
        server.shutdown()
    }

    @Test
    internal fun `find tags by name`() {
        val tagUrls =
            listOf("localhost/sha256:1", "localhost/sha256:2")

        val response = imageRegistryService.findTagsByName(tagUrls)
        assertThat(response).isNotNull()
        assertThat(response.success).isTrue()
        assertThat(response.items).isNotEmpty()
    }
}
