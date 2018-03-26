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
        val dcBuilder = DeploymentConfigDataBuilder()
        val istBuilder = ImageStreamTagDataBuilder()
        every { openShiftService.imageStreamTag(dcBuilder.dcNamespace, dcBuilder.dcName, "tag") } returns istBuilder.build()

        val imageDetails = imageService.getImageDetails(dcBuilder.build())
        assert(imageDetails?.dockerImageReference).isEqualTo(istBuilder.dockerImageReference)
    }

    @Test
    fun `should split assignment strings on assignment operator to create a map`() {
        val map = ImageService.assignmentStringsToMap(listOf("KEY=VALUE", "KEY2=VALUE2"))
        assert(map).isEqualTo(mapOf("KEY" to "VALUE", "KEY2" to "VALUE2"))
    }
}