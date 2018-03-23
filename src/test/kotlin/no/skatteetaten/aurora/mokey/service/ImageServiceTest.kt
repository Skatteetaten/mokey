package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageStreamTagDataBuilder
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
    fun `get image details`() {
        val dc = DeploymentConfigDataBuilder().build()
        val imageStreamTag = ImageStreamTagDataBuilder().build()
        every { openShiftService.imageStreamTag("namespace", "app-name", "tag") } returns imageStreamTag

        val imageDetails = imageService.getImageDetails(dc)
        assert(imageDetails?.dockerImageReference).isEqualTo("dockerImageReference")
    }
}