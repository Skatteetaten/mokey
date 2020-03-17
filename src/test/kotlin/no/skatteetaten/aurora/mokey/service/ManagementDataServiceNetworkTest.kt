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
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.execute
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.url
import no.skatteetaten.aurora.mokey.PodDataBuilder
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ManagementDataServiceNetworkTest {

    private val server = MockWebServer()
    private val service = ManagementDataService(
        OpenShiftManagementClient(
            KubernetesReactorClient(server.url, "test-token"), false
        )
    )

    private val linksResponse = jsonResponse(
        HalResource(_links = Links().apply {
            add("health", "/health")
        })
    )

    @AfterEach
    fun tearDown() {
        kotlin.runCatching {
            server.shutdown()
        }
    }

    @Test
    fun `Retry on network failure`() {
        val healthErrorResponse = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val healthOkResponse = jsonResponse("""{"status":"UP","details":{"diskSpace":{"status":"UP"}}}""")

        val requests = server.execute(linksResponse, healthErrorResponse, healthErrorResponse, healthOkResponse) {
            val managementData = runBlocking {
                service.load(PodDataBuilder().build(), ":8081/links")
            }

            assertThat(managementData.health?.errorMessage).isNull()
            assertThat(managementData.health?.resultCode).isEqualTo("OK")
        }

        assertThat(requests).hasSize(4)
    }

    @Test
    fun `Retry for disconnect after request`() {
        val error = MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
        val ok = jsonResponse("""{"status":"UP","details":{"diskSpace":{"status":"UP"}}}""")

        val requests = server.execute(linksResponse, error, error, ok) {
            val managementData = runBlocking {
                service.load(PodDataBuilder().build(), ":8081/links")
            }

            assertThat(managementData.health?.errorMessage).isNull()
            assertThat(managementData.health?.resultCode).isEqualTo("OK")
        }

        assertThat(requests).hasSize(4)
    }

    @Test
    fun `Time out when no response is returned`() {
        val error = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)

        server.execute(linksResponse, error) {
            runBlocking {
                val managementData = service.load(PodDataBuilder().build(), ":8081/links")
                assertThat(managementData.health?.errorMessage)
                    .isEqualTo("Timed out getting management interface for url=namespaces/namespace/pods/name:8081/proxy/health")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
        value = SocketPolicy::class,
        mode = EnumSource.Mode.EXCLUDE,
        names = ["DISCONNECT_AFTER_REQUEST", "NO_RESPONSE"]
    )
    fun `Throw IOException when null is returned`(socketPolicy: SocketPolicy) {
        val error = MockResponse().setSocketPolicy(socketPolicy)

        server.execute(linksResponse, error) {
            runBlocking {
                val managementData = service.load(newPod {
                    metadata = newObjectMeta {
                        name = "name1"
                        namespace = "namespace1"
                    }
                }, ":8081/links")
                assertThat(managementData.health?.errorMessage).isEqualTo("No response for url=namespaces/namespace1/pods/name1:8081/proxy/health")
            }
        }
    }

    @Test
    fun `Timeout given no response from health`() {
        val healthTimeoutResponse = MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)

        val requests = server.execute(linksResponse, healthTimeoutResponse) {
            val managementData = runBlocking {
                service.load(PodDataBuilder().build(), ":8081/links")
            }

            assertThat(managementData.health?.errorMessage).isNotNull()
        }

        assertThat(requests).hasSize(2)
    }
}
