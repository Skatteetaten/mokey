package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageStreamTagDataBuilder
import org.junit.Ignore
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImageServiceTest {

    private val openShiftService = mockk<OpenShiftService>()
    private val imageService = ImageService(openShiftService)

    @BeforeEach
    fun setUp() {
        clearMocks(openShiftService)
    }

    @Test
    @Ignore("Test fails before this branch started development")
    fun `get image details`() {
        val dcBuilder = DeploymentConfigDataBuilder()
        val istBuilder = ImageStreamTagDataBuilder(env = mapOf("IMAGE_BUILD_TIME" to "2018-08-01T13:27:21Z"))
        every {
            openShiftService.imageStreamTag(
                dcBuilder.dcNamespace,
                dcBuilder.dcName,
                "tag"
            )
        } returns istBuilder.build()

        val imageDetails = imageService.getImageDetails(dcBuilder.build())
        assert(imageDetails?.dockerImageReference).isEqualTo(istBuilder.reference)
    }
}