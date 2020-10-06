package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import com.fkorotkov.kubernetes.newKubernetesList
import com.fkorotkov.kubernetes.newNamespaceList
import com.fkorotkov.kubernetes.newReplicationControllerList
import com.fkorotkov.openshift.newProjectList
import io.fabric8.kubernetes.api.model.KubernetesList
import io.fabric8.kubernetes.internal.KubernetesDeserializer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.HttpMock
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.MockRules
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.initHttpMockServer
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import no.skatteetaten.aurora.mokey.AddressBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentBuilder
import no.skatteetaten.aurora.mokey.AuroraStatusBuilder
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageDetailsDataBuilder
import no.skatteetaten.aurora.mokey.NamespaceDataBuilder
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.ProjectDataBuilder
import no.skatteetaten.aurora.mokey.ReplicationControllerDataBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.util.SocketUtils
import java.time.Duration

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ApplicationDataServiceTest {

    private val port = SocketUtils.findAvailableTcpPort()
    private val server = initHttpMockServer {
        rulePathEndsWith("namespaces") {
            jsonResponse(newNamespaceList {
                items = listOf(NamespaceDataBuilder("aurora-dev").build())
            })
        }

        rulePathEndsWith("projects") {
            jsonResponse(newProjectList {
                items = listOf(ProjectDataBuilder("aurora-dev").build())
            })
        }

        rulePathContains("applicationdeployments") {
            jsonResponse(newKubernetesList {
                items = listOf(ApplicationDeploymentBuilder().build())
            })
        }

        rulePathContains("deploymentconfigs") {
            jsonResponse(DeploymentConfigDataBuilder().build())
        }

        rulePathContains("replicationcontrollers") {
            jsonResponse(newReplicationControllerList {
                items = listOf(ReplicationControllerDataBuilder().build())
            })
        }
    }

    private val coroutinesClient = KubernetesCoroutinesClient("http://localhost:$port", "test-token")

    private val calculator = mockk<AuroraStatusCalculator>()
    private val podService = mockk<PodService>()
    private val addressService = mockk<AddressService>()
    private val imageService = mockk<ImageService>()

    private val dataServiceOpenShift =
        ApplicationDataServiceOpenShift(
            OpenShiftServiceAccountClient(coroutinesClient),
            calculator,
            podService,
            addressService,
            imageService,
            true
        )
    private val dataService = ApplicationDataService(
        dataServiceOpenShift,
        OpenShiftUserClient(coroutinesClient),
        "aurora",
        mockk(relaxed = true),
        Duration.ofMinutes(3)
    )

    @BeforeEach
    fun setUp() {
        KubernetesDeserializer.registerCustomKind(
            "skatteetaten.no/v1",
            "ApplicationDeploymentList",
            KubernetesList::class.java
        )

        KubernetesDeserializer.registerCustomKind(
            "skatteetaten.no/v1",
            "ApplicationDeployment",
            ApplicationDeployment::class.java
        )
        every { calculator.calculateAuroraStatus(any(), any(), any()) } returns AuroraStatusBuilder().build()
        coEvery { podService.getPodDetails(any(), any(), any()) } returns listOf(PodDetailsDataBuilder().build())
        coEvery { addressService.getAddresses(any(), any()) } returns listOf(AddressBuilder().build())
        coEvery {
            imageService.getImageDetailsFromImageStream(
                any(),
                any(),
                any()
            )
        } returns ImageDetailsDataBuilder().build()

        server.start(port)
    }

    @AfterEach
    fun tearDown() {
        HttpMock.clearAllHttpMocks()
    }

    @Test
    fun `Initialize cache and read application data`() {
        dataService.cache()
        val applicationData = dataService.findAllApplicationData(listOf("aurora"))
        val ad =
            dataService.findApplicationDataByApplicationDeploymentId(applicationData.first().applicationDeploymentId)

        assertThat(applicationData.first().affiliation).isEqualTo("aurora")
        assertThat(ad?.affiliation).isEqualTo("aurora")
    }

    @Test
    fun `Initialize cache and read public application data`() {
        dataService.cache()
        val publicData = dataService.findAllPublicApplicationData(listOf("aurora"))
        val pd1 =
            dataService.findPublicApplicationDataByApplicationDeploymentId(publicData.first().applicationDeploymentId)
        val pd2 = dataService.findAllPublicApplicationDataByApplicationId(publicData.first().applicationId!!)

        assertThat(publicData.first().affiliation).isEqualTo("aurora")
        assertThat(pd1?.affiliation).isEqualTo("aurora")
        assertThat(pd2.first().affiliation).isEqualTo("aurora")
    }

    @Test
    fun `Read public application data with ApplicationDeploymentRef`() {
        dataService.cache()
        val publicData = dataService.findAllPublicApplicationData(listOf("aurora"))
        val applicationDeploymentRef =
            ApplicationDeploymentRef(publicData.first().environment, publicData.first().applicationDeploymentName)

        val pd1 =
            dataService.findPublicApplicationDataByApplicationDeploymentRef(listOf(applicationDeploymentRef))
        val pd2 = dataService.findAllPublicApplicationDataByApplicationId(publicData.first().applicationId!!)

        assertThat(publicData.first().affiliation).isEqualTo("aurora")
        assertThat(pd1.first().affiliation).isEqualTo("aurora")
        assertThat(pd2.first().affiliation).isEqualTo("aurora")
    }

    @Test
    fun `Return empty list if no ApplicationDeployment is found`() {
        val applicationData = dataService.findPublicApplicationDataByApplicationDeploymentRef(
            listOf(ApplicationDeploymentRef("unknown", "unknown"))
        )
        assertThat(applicationData).isEmpty()
    }

    @Test
    fun `Initialize cache and find affiliations`() {
        dataService.cacheAtStartup()
        val visibleAffiliations = dataService.findAllVisibleAffiliations()
        val allAffiliations = dataService.findAllAffiliations()

        assertThat(visibleAffiliations).hasSize(1)
        assertThat(allAffiliations).hasSize(1)
        assertThat(visibleAffiliations.first()).isEqualTo("aurora")
        assertThat(allAffiliations.first()).isEqualTo("aurora")
    }

    @Test
    fun `Create disabled application for type Job`() {
        server.updateRule("applicationdeployments") {
            jsonResponse(newKubernetesList {
                items = listOf(ApplicationDeploymentBuilder("Job").build())
            })
        }

        dataService.cacheAtStartup()

        val affiliations = dataService.findAllAffiliations()
        val applicationData = dataService.findAllApplicationData(listOf("aurora"))
        assertThat(affiliations).hasSize(1)
        assertThat(applicationData.first().auroraStatus.level).isEqualTo(AuroraStatusLevel.OFF)
    }

    @Test
    fun `Initialize cache for runnableType not Deployment`() {
        server.updateRule("applicationdeployments") {
            jsonResponse(newKubernetesList {
                items = listOf(ApplicationDeploymentBuilder("type").build())
            })
        }

        dataService.cacheAtStartup()
        val affiliations = dataService.findAllAffiliations()
        assertThat(affiliations).hasSize(1)
    }

    @Test
    fun `Initialize cache, add entry and remove entry`() {
        dataService.cacheAtStartup()
        val affiliations = dataService.findAllAffiliations()
        assertThat(affiliations).hasSize(1)

        server.mockRules.clear() // clear all rules
        registerProjectsResponse() // add response for /projects
        registerEmptyResponses() // all other requests will return empty response

        dataService.cache()
        val updatedAffiliations = dataService.findAllAffiliations()
        assertThat(updatedAffiliations).hasSize(0)
    }

    @Test
    fun `Refresh cache with unknown applicationDeploymentId`() {
        assertThat { dataService.refreshItem("abc") }.isFailure().isInstanceOf(IllegalArgumentException::class)
    }

    private fun registerProjectsResponse() {
        server.mockRules.add(MockRules({ path?.endsWith("namespaces") }, {
            jsonResponse(newNamespaceList {
                items = listOf(NamespaceDataBuilder("aurora-dev").build())
            })
        }))
    }

    private fun registerEmptyResponses() {
        server.mockRules.add(MockRules({ true }, {
            jsonResponse()
        }))
    }
}

class ApplicationDataServiceTryCatchTest {

    @Test
    fun `Catch exception in refresh cache`() {
        val dataServiceOpenshift = mockk<ApplicationDataServiceOpenShift>()
        coEvery { dataServiceOpenshift.findAndGroupAffiliations() } throws
            RuntimeException(
                "test test",
                IllegalArgumentException("root cause message")
            )
        val service = ApplicationDataService(dataServiceOpenshift, mockk(), "", mockk(), Duration.ofMinutes(3))
        service.cache()

        coVerify { dataServiceOpenshift.findAndGroupAffiliations() }
    }
}
