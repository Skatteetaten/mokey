package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.fabric8.openshift.api.model.Image
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.AuroraResponseBuilder
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageStreamTagDataBuilder
import no.skatteetaten.aurora.mokey.ReplicationControllerDataBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImageServiceTest {

    private val openShiftService = mockk<OpenShiftService>()
    private val imageRegistryService = mockk<ImageRegistryService>()

    private val imageService = ImageService(openShiftService, imageRegistryService)

    @BeforeEach
    fun setUp() {
        clearMocks(openShiftService)
    }

    @Test
    fun `get image details when running rc is not latest`() {
        val dcBuilder = DeploymentConfigDataBuilder(dcDeployTag = "foobar:tag")
        val rcBuilder = ReplicationControllerDataBuilder()
        val arBuilder = AuroraResponseBuilder()

        every {
            imageRegistryService.findTagsByName(
                listOf("docker-registry/group/name/sha256:123hash")
            )
        } returns arBuilder.build()

        val imageDetails = imageService.getImageDetails(dcBuilder.dcNamespace, "foobar", false, rcBuilder.build())
        assertThat(imageDetails?.dockerImageReference).isEqualTo("docker-registry/group/name@sha256:123hash")
        assertThat(imageDetails?.auroraVersion).isEqualTo(arBuilder.build().items[0].auroraVersion)
    }

    @Test
    fun `get image details when running rc is latest`() {
        val dcBuilder = DeploymentConfigDataBuilder(dcDeployTag = "foobar:tag")
        val istBuilder = ImageStreamTagDataBuilder(env = mapOf("IMAGE_BUILD_TIME" to "2018-08-01T13:27:21Z"))

        every {
            openShiftService.imageStreamTag(
                dcBuilder.dcNamespace,
                "foobar",
                "default"
            )
        } returns istBuilder.build()

        val imageDetails = imageService.getImageDetails(dcBuilder.dcNamespace, "foobar", true, null)
        assertThat(imageDetails?.dockerImageReference).isEqualTo("dockerImageReference")
    }

    @Test
    fun `get image env`() {
        val json = """{ "dockerImageMetadata": { "Config": { "Env": ["Path=/usr/local"] } } }""".trimIndent()
        val image = jacksonObjectMapper().readValue<Image>(json)
        assertThat(image.env.keys.first()).isEqualTo("Path")
        assertThat(image.env.values.first()).isEqualTo("/usr/local")
    }
}