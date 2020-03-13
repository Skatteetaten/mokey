package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.fkorotkov.kubernetes.newKubernetesList
import com.fkorotkov.kubernetes.newReplicationControllerList
import com.fkorotkov.openshift.newProjectList
import io.fabric8.kubernetes.api.model.KubernetesList
import io.fabric8.kubernetes.internal.KubernetesDeserializer
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.HttpMock
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.httpMockServer
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import no.skatteetaten.aurora.mokey.AddressBuilder
import no.skatteetaten.aurora.mokey.ApplicationDeploymentBuilder
import no.skatteetaten.aurora.mokey.AuroraStatusBuilder
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.ImageDetailsDataBuilder
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.ProjectDataBuilder
import no.skatteetaten.aurora.mokey.ReplicationControllerDataBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.util.SocketUtils
import org.springframework.web.reactive.function.client.WebClient

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ApplicationDataServiceTest {

    private val port = SocketUtils.findAvailableTcpPort()
    private val coroutinesClient = KubernetesCoroutinesClient(
        KubernetesReactorClient(
            WebClient.create("http://localhost:$port"), object : TokenFetcher {
                override fun token() = "test-token"
            }, RetryConfiguration()
        )
    )

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
            imageService
        )
    private val dataService = ApplicationDataService(
        dataServiceOpenShift,
        OpenShiftUserClient(coroutinesClient),
        "aurora",
        mockk(relaxed = true)
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

        val projects = newProjectList {
            items = listOf(ProjectDataBuilder("aurora-dev").build())
        }

        val applicationDeployments = newKubernetesList {
            items = listOf(ApplicationDeploymentBuilder().build())
        }

        val dc = DeploymentConfigDataBuilder().build()
        val replicationControllers = newReplicationControllerList {
            items = listOf(ReplicationControllerDataBuilder().build())
        }

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

        httpMockServer(port) {
            rule({ path?.endsWith("projects") }) {
                jsonResponse(projects)
            }

            rule({ path?.contains("applicationdeployments") }) {
                jsonResponse(applicationDeployments)
            }

            rule({ path?.contains("deploymentconfigs") }) {
                jsonResponse(dc)
            }

            rule({ path?.contains("replicationcontrollers") }) {
                jsonResponse(replicationControllers)
            }
        }
    }

    @AfterEach
    fun tearDown() {
        HttpMock.clearAllHttpMocks()
    }

    @Test
    fun `Initialize cache and read application data`() {
        dataService.cache()
        val applicationData = dataService.findAllApplicationData(listOf("aurora"))
        val ad = dataService.findApplicationDataByApplicationDeploymentId(applicationData.first().applicationDeploymentId)

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
    fun `Initialize cache and find affiliations`() {
        dataService.cacheAtStartup()
        val visibleAffiliations = dataService.findAllVisibleAffiliations()
        val allAffiliations = dataService.findAllAffiliations()

        assertThat(visibleAffiliations).hasSize(1)
        assertThat(allAffiliations).hasSize(1)
        assertThat(visibleAffiliations.first()).isEqualTo("aurora")
        assertThat(allAffiliations.first()).isEqualTo("aurora")
    }
}
