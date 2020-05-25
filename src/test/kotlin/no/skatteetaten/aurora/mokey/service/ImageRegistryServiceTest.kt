package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.setJsonFileAsBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
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
        server.execute(MockResponse().setJsonFileAsBody("manifest.json")) {
            runBlocking {
                val response = imageRegistryService.findTagsByName(tagUrls)
                assertThat(response).isNotNull()
            }
        }
    }

    @Test
    fun `find tags by name, response contains failure`() {
        server.execute(
            AuroraResponse<ImageTagResource>(
                success = false,
                message = "test failure"
            )
        ) {
            runBlocking {
                assertThat {
                    imageRegistryService.findTagsByName(tagUrls)
                }.isFailure().cause().isNotNull().isInstanceOf(ServiceException::class)
            }
        }
    }

    @Test
    fun `throws ServiceException if response is 404`() {
        server.execute(MockResponse().setBody("{}").setResponseCode(404)) {
            runBlocking {
                assertThat {
                    imageRegistryService.findTagsByName(tagUrls)
                }.isFailure().isInstanceOf(ServiceException::class)
            }
        }
    }
}
