package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.fkorotkov.kubernetes.newObjectMeta
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.AuroraApplicationInstanceDataBuilder
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageDetailsDataBuilder
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.ProjectDataBuilder
import no.skatteetaten.aurora.mokey.ReplicationControllerDataBuilder
import no.skatteetaten.aurora.mokey.extensions.LABEL_AFFILIATION
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.ServiceAddress
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant

class ApplicationDataServiceOpenShiftTest {

    private val openShiftService = mockk<OpenShiftService>()
    private val auroraStatusCalculator = mockk<AuroraStatusCalculator>()
    private val podService = mockk<PodService>()
    private val imageService = mockk<ImageService>()
    private val addressService = mockk<AddressService>()
    private val applicationDataServiceOpenShift = ApplicationDataServiceOpenShift(
            openShiftService,
            auroraStatusCalculator,
            podService,
            addressService,
            imageService
    )

    @BeforeEach
    fun setUp() {
        clearMocks(openShiftService, auroraStatusCalculator, podService, imageService, addressService)
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
        val appBuilder = AuroraApplicationInstanceDataBuilder()

        val dcBuilder = DeploymentConfigDataBuilder()

        val appInstance = appBuilder.build()
        every { openShiftService.auroraApplicationInstances(dcBuilder.dcAffiliation) } returns listOf(appInstance)

        val dc = dcBuilder.build()
        every { openShiftService.dc(dcBuilder.dcNamespace, dcBuilder.dcName) } returns dc
        val replicationController = ReplicationControllerDataBuilder().build()
        every { openShiftService.rc(dcBuilder.dcNamespace, "app-name-1") } returns replicationController

        val podDetails = PodDetailsDataBuilder().build()
        every { podService.getPodDetails(appInstance) } returns listOf(podDetails)

        val imageDetails = ImageDetailsDataBuilder().build()
        every { imageService.getImageDetails(dc) } returns imageDetails

        val addresses = listOf(ServiceAddress(URI.create("http://app-name"), Instant.EPOCH))
        every { addressService.getAddresses(dcBuilder.dcNamespace, "app-name") } returns addresses

        every { auroraStatusCalculator.calculateStatus(any(), any()) } returns AuroraStatus(AuroraStatusLevel.HEALTHY, "")

        val id = "affiliation::affiliation::app-name"
        val applicationData = applicationDataServiceOpenShift.findApplicationDataByInstanceId(id)

        assert(applicationData?.name).isEqualTo(dcBuilder.dcName)
        assert(applicationData?.auroraStatus?.level).isEqualTo(AuroraStatusLevel.HEALTHY)
    }
}