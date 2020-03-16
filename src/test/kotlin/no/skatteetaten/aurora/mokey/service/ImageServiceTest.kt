package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.mockmvc.extensions.TestObjectMapperConfigurer
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.url
import no.skatteetaten.aurora.mokey.ImageStreamTagDataBuilder
import no.skatteetaten.aurora.mokey.ImageTagResourceBuilder
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ImageServiceTest {

    private val server = MockWebServer()
    private val client = OpenShiftServiceAccountClient(
        KubernetesCoroutinesClient(server.url, "test-token")
    )

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val imageRegistryClient = ImageRegistryClient(WebClient.create(server.url), objectMapper)
    private val imageService = ImageService(client, ImageRegistryService(imageRegistryClient), false)

    @BeforeEach
    fun setUp() {
        TestObjectMapperConfigurer.objectMapper = objectMapper
    }

    @AfterEach
    fun tearDown() {
        TestObjectMapperConfigurer.reset()
        kotlin.runCatching {
            server.shutdown()
        }
    }

    @Test
    fun `get image details`() {
        val tag = ImageTagResourceBuilder().build()
        val response = AuroraResponse(items = listOf(tag))

        server.execute(response) {
            runBlocking {
                val imageDetails = imageService.getImageDetails("docker-registry/group/name@sha256:123hash")

                assertThat(imageDetails?.environmentVariables?.size).isEqualTo(5)
                assertThat(imageDetails?.auroraVersion).isEqualTo(tag.auroraVersion)
                assertThat(imageDetails?.imageBuildTime).isNotNull()
            }
        }
    }

    @Test
    fun `get image details from image stream`() {
        val imageStreamTag = ImageStreamTagDataBuilder().build()

        server.execute(imageStreamTag) {
            runBlocking {
                val imageDetails = imageService.getImageDetailsFromImageStream("namespace", "name", "tag")
                assertThat(imageDetails?.dockerImageReference).isEqualTo("dockerImageReference")
            }
        }
    }
}
