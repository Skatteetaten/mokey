package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.url
import no.skatteetaten.aurora.mokey.ApplicationDeploymentBuilder
import no.skatteetaten.aurora.mokey.PodDataBuilder
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.DiscoveryLink
import no.skatteetaten.aurora.mokey.model.DiscoveryResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test

class PodServiceTest {
    private val server = MockWebServer()
    private val podService = PodService(
        OpenShiftServiceAccountClient(KubernetesCoroutinesClient(server.url, "test-token")),
        ManagementDataService(
            OpenShiftManagementClient(
                KubernetesReactorClient(
                    server.url,
                    "test-token"
                ),
                false
            )
        ),
    )

    @Test
    fun `Get pod details`() {
        server.execute(
            listOf(PodDataBuilder().build()),
            DiscoveryResponse(mapOf("discovery" to DiscoveryLink("/discovery"))),
        ) {
            runBlocking {
                val podDetails = podService.getPodDetails(
                    ApplicationDeploymentBuilder().build(),
                    DeployDetails(0, 0),
                    emptyMap(),
                )

                assertThat(podDetails).hasSize(1)

                val first = podDetails.first()

                assertThat(first.openShiftPodExcerpt.name).isEqualTo("name")
                assertThat(first.managementData.links.resultCode).isEqualTo("OK")
                assertThat(first.managementData.links.createdAt).isNotNull()
            }
        }
    }
}
