package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ReplicationController
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageDetailsDataBuilder
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.ProjectDataBuilder
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApplicationDataServiceOpenShiftTest {

    private val openShiftService = mockk<OpenShiftService>()
    private val auroraStatusCalculator = mockk<AuroraStatusCalculator>()
    private val podService = mockk<PodService>()
    private val imageService = mockk<ImageService>()
    private val applicationDataServiceOpenShift = ApplicationDataServiceOpenShift(
            openShiftService,
            auroraStatusCalculator,
            podService,
            imageService
    )

    @BeforeEach
    fun setUp() {
        clearMocks(openShiftService, auroraStatusCalculator, podService, imageService)
    }

    @Test
    fun `find all affiliations`() {
        val project = ProjectDataBuilder().build()
        every { openShiftService.projects() } returns listOf(project)
        val affiliations = applicationDataServiceOpenShift.findAllAffiliations()

        assert(affiliations) {
            hasSize(1)
            contains("affiliation")
        }
    }

    @Test
    fun `find application data by id`() {
        val dc = DeploymentConfigDataBuilder().build()
        every { openShiftService.deploymentConfigs("affiliation") } returns listOf(dc)
        every { openShiftService.rc("namespace", "name-1") } returns ReplicationController().apply { metadata = ObjectMeta().apply { annotations = mapOf("openshift.io/deployment.phase" to "deploymentPhase") } }

        val podDetails = PodDetailsDataBuilder().build()
        every { podService.getPodDetails(dc) } returns listOf(podDetails)

        val imageDetails = ImageDetailsDataBuilder().build()
        every { imageService.getImageDetails(dc) } returns imageDetails

        every { auroraStatusCalculator.calculateStatus(any(), any()) } returns AuroraStatus(AuroraStatusLevel.HEALTHY, "")

        val id = "affiliation::affiliation::name"
        val applicationData = applicationDataServiceOpenShift.findApplicationDataById(id)

        assert(applicationData?.name).isEqualTo("name")
        assert(applicationData?.auroraStatus?.level).isEqualTo(AuroraStatusLevel.HEALTHY)
    }


}