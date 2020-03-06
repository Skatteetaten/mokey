package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.url
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.web.reactive.function.client.WebClient
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ManagementDataServiceNetworkTest {

    private val server = MockWebServer()
    private val service = ManagementDataService(
        OpenShiftManagementClient(
            KubernetesReactorClient(
                WebClient.create(server.url), object : TokenFetcher {
                    override fun token() = "test-token"
                },
                RetryConfiguration()
            ), false
        )
    )

    private val linksResponse = jsonResponse(
        HalResource(_links = Links().apply {
            add("health", "/health")
        })
    )

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Retry on network failure`() {
        // val healthErrorResponse = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val healthErrorResponse = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        val healthOkResponse = jsonResponse("""{"status":"UP","details":{"diskSpace":{"status":"UP"}}}""")

        val requests = server.execute(linksResponse, healthErrorResponse, healthErrorResponse, healthOkResponse) {
            val managementData = runBlocking {
                service.load(newPod {
                    metadata = newObjectMeta {
                        name = "name1"
                        namespace = "namespace1"
                    }
                }, ":8081/links")
            }

            assertThat(managementData.health?.errorMessage).isNull()
            assertThat(managementData.health?.resultCode).isEqualTo("OK")
        }

        assertThat(requests).hasSize(4)
    }

    @Test
    fun `Timeout given no response from health`() {
        val healthTimeoutResponse = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)

        val requests = server.execute(linksResponse, healthTimeoutResponse) {
            val managementData = runBlocking {
                service.load(newPod {
                    metadata = newObjectMeta {
                        name = "name2"
                        namespace = "namespace2"
                    }
                }, ":8081/links")
            }

            assertThat(managementData.health?.errorMessage).isNotNull()
        }

        assertThat(requests).hasSize(2)
    }
}