package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.openshift.api.model.Project
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.AuroraApplicationDeploymentDataBuilder
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageDetailsDataBuilder
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.ProjectDataBuilder
import no.skatteetaten.aurora.mokey.ReplicationControllerDataBuilder
import no.skatteetaten.aurora.mokey.extensions.deployTag
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.ServiceAddress
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant

class ApplicationDataServiceOpenShiftTest {

    private val openShiftService = mockk<OpenShiftService>(relaxed = true)
    private val auroraStatusCalculator = mockk<AuroraStatusCalculator>()
    private val podService = mockk<PodService>()
    private val imageService = mockk<ImageService>()
    private val addressService = mockk<AddressService>(relaxed = true)
    private val imageRegistryService = mockk<ImageRegistryService>()
    private val applicationDataServiceOpenShift = ApplicationDataServiceOpenShift(
        openshiftService = openShiftService,
        auroraStatusCalculator = auroraStatusCalculator,
        podService = podService,
        addressService = addressService,
        imageService = imageService
    )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
    }

    @Test
    fun `find all affiliations`() {
        val project = ProjectDataBuilder().build()
        every { openShiftService.projects() } returns listOf(project)
        val affiliations = applicationDataServiceOpenShift.findAndGroupAffiliations()

        assertThat(affiliations.size).isEqualTo(1)
        assertThat(affiliations.containsKey("affiliation")).isTrue()
    }

    @Test
    fun `find paused application data by id`() {
        val dcBuilder = DeploymentConfigDataBuilder(pause = true)
        val appDeployment = AuroraApplicationDeploymentDataBuilder().build()
        val dc = dcBuilder.build()
        val replicationController = ReplicationControllerDataBuilder().build()
        val podDetails = PodDetailsDataBuilder().build()
        val imageDetails = ImageDetailsDataBuilder().build()
        val addresses = listOf(ServiceAddress(URI.create("http://app-name"), Instant.EPOCH))

        every { openShiftService.projects() } returns listOf(
            Project("1", "Project", ObjectMeta().apply { name = dcBuilder.dcNamespace }, null, null)
        )
        every { openShiftService.applicationDeployments(dcBuilder.dcNamespace) } returns listOf(appDeployment)
        every { openShiftService.dc(dcBuilder.dcNamespace, dcBuilder.dcName) } returns dc
        every { openShiftService.rc(dcBuilder.dcNamespace, any()) } returns replicationController
        every { podService.getPodDetails(appDeployment, any()) } returns listOf(podDetails)
        every {
            imageService.getImageDetailsFromImageStream(
                dc.metadata.namespace,
                dc.metadata.name,
                "default"

            )
        } returns imageDetails
        every { addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName) } returns addresses
        every { auroraStatusCalculator.calculateAuroraStatus(any(), any(), any()) } returns AuroraStatus(HEALTHY)

        val applicationData = findAllApplicationData(listOf(dcBuilder.dcAffiliation)).first()

        assertThat(applicationData.applicationDeploymentId).isEqualTo(appDeployment.spec.applicationDeploymentId)
        assertThat(applicationData.applicationDeploymentName).isEqualTo(appDeployment.spec.applicationDeploymentName)
        assertThat(applicationData.applicationId).isEqualTo(appDeployment.spec.applicationId)
        assertThat(applicationData.applicationName).isEqualTo(appDeployment.spec.applicationName)
        assertThat(applicationData.auroraStatus.level).isEqualTo(HEALTHY)
        assertThat(applicationData.publicData.message).isEqualTo("message")
        assertThat(applicationData.deployDetails?.paused).isEqualTo(true)
    }

    @Test
    fun `find application data by id`() {
        val dcBuilder = DeploymentConfigDataBuilder()
        val appDeployment = AuroraApplicationDeploymentDataBuilder(databases = listOf("123-456-789")).build()
        val dc = dcBuilder.build()
        val replicationController = ReplicationControllerDataBuilder().build()
        val podDetails = PodDetailsDataBuilder().build()
        val imageDetails = ImageDetailsDataBuilder().build()
        val addresses = listOf(ServiceAddress(URI.create("http://app-name"), Instant.EPOCH))

        every { openShiftService.projects() } returns listOf(
            Project("1", "Project", ObjectMeta().apply { name = dcBuilder.dcNamespace }, null, null)
        )
        every { openShiftService.applicationDeployments(dcBuilder.dcNamespace) } returns listOf(appDeployment)
        every { openShiftService.dc(dcBuilder.dcNamespace, dcBuilder.dcName) } returns dc
        every { openShiftService.rc(dcBuilder.dcNamespace, any()) } returns replicationController
        every { podService.getPodDetails(appDeployment, any()) } returns listOf(podDetails)
        every {
            imageService.getImageDetailsFromImageStream(
                dc.metadata.namespace,
                dc.metadata.name,
                "default"
            )
        } returns imageDetails
        every { addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName) } returns addresses
        every { auroraStatusCalculator.calculateAuroraStatus(any(), any(), any()) } returns AuroraStatus(HEALTHY)

        val applicationData = findAllApplicationData(listOf(dcBuilder.dcAffiliation)).first()

        assertThat(applicationData.applicationDeploymentId).isEqualTo(appDeployment.spec.applicationDeploymentId)
        assertThat(applicationData.applicationDeploymentName).isEqualTo(appDeployment.spec.applicationDeploymentName)
        assertThat(applicationData.applicationId).isEqualTo(appDeployment.spec.applicationId)
        assertThat(applicationData.applicationName).isEqualTo(appDeployment.spec.applicationName)
        assertThat(applicationData.auroraStatus.level).isEqualTo(HEALTHY)
        assertThat(applicationData.updatedBy).isEqualTo("linus")
        assertThat(applicationData.publicData.message).isEqualTo("message")
        assertThat(applicationData.deployDetails?.paused).isEqualTo(false)
        assertThat(applicationData.databases).contains("123-456-789")
    }

    @Test
    fun `should exclude application data for failing deployments`() {
        val dcBuilder = DeploymentConfigDataBuilder()
        val appDeployment = AuroraApplicationDeploymentDataBuilder().build()

        every { openShiftService.projects() } returns listOf(
            Project("1", "Project", ObjectMeta().apply { name = dcBuilder.dcNamespace }, null, null)
        )

        every { openShiftService.applicationDeployments(dcBuilder.dcNamespace) } returns listOf(appDeployment)
        every { openShiftService.dc(dcBuilder.dcNamespace, dcBuilder.dcName) } returns null

        val applicationData = findAllApplicationData(listOf(dcBuilder.dcAffiliation)).first()
        assertThat(applicationData.deployDetails).isNull()
        assertThat(applicationData.applicationDeploymentId).isEqualTo(appDeployment.spec.applicationDeploymentId)
        assertThat(applicationData.applicationDeploymentName).isEqualTo(appDeployment.spec.applicationDeploymentName)
        assertThat(applicationData.applicationId).isEqualTo(appDeployment.spec.applicationId)
        assertThat(applicationData.applicationName).isEqualTo(appDeployment.spec.applicationName)
        assertThat(applicationData.auroraStatus.level).isEqualTo(expected = AuroraStatusLevel.OFF)
    }

    @Test
    fun `should return running rc and handle when latest version is 1`() {
        every {
            openShiftService.rc("aurora-dev", "mokey-1")
        } returns ReplicationControllerDataBuilder(
            rcPhase = "Complete",
            rcDeployTag = "latest",
            rcStatusReplicas = 4
        ).build()
        val runningRc = applicationDataServiceOpenShift.getRunningRc("aurora-dev", "mokey", 1)
        assertThat(runningRc?.deployTag).isEqualTo("latest")
    }

    @Test
    fun `should return null when no rc is running`() {
        every {
            openShiftService.rc("aurora-dev", "mokey-1")
        } returns ReplicationControllerDataBuilder(
            rcPhase = "Failed",
            rcDeployTag = "latest",
            rcStatusReplicas = 4
        ).build()
        val runningRc = applicationDataServiceOpenShift.getRunningRc("aurora-dev", "mokey", 1)
        assertThat(runningRc).isEqualTo(null)
    }

    private fun findAllApplicationData(
        affiliations: List<String> = emptyList(),
        ids: List<String> = emptyList()
    ): List<ApplicationData> {
        val affiliationGroups = applicationDataServiceOpenShift.findAndGroupAffiliations(affiliations)
        return affiliationGroups.flatMap {
            applicationDataServiceOpenShift.findAllApplicationDataForEnv(it.value, ids)
        }
    }
}