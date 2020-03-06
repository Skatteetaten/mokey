package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.HttpMock
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.httpMockServer
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import okhttp3.mockwebserver.MockResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.util.SocketUtils
import org.springframework.web.reactive.function.client.WebClient
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

class ManagementDataServiceTest {

    private val port = SocketUtils.findAvailableTcpPort()
    private val service = ManagementDataService(
        OpenShiftManagementClient(
            KubernetesReactorClient(
                WebClient.create("http://localhost:$port"), object : TokenFetcher {
                    override fun token() = "test-token"
                },
                RetryConfiguration()
            ), false
        )
    )

    @AfterEach
    fun tearDown() {
        HttpMock.clearAllHttpMocks()
    }

    @Test
    fun `management health should fail with text response`() {
        httpMockServer(port) {
            rule({ path?.endsWith("links") }) {
                jsonResponse(
                    HalResource(_links = Links().apply {
                        add("health", "/health")
                    })
                )
            }

            rule({ path?.endsWith("health") }) {
                MockResponse().setBody("Foo")
            }
        }

        val managementData = runBlocking {
            service.load(newPod {
                metadata = newObjectMeta {
                    name = "name1"
                    namespace = "namespace1"
                }
            }, ":8081/links")
        }
        assertThat(managementData).isNotNull()
        assertThat(managementData.health?.resultCode).isEqualTo("INVALID_JSON")
    }

    @Test
    fun `management health should fail with wrong status value`() {
        httpMockServer(port) {
            rule({ path?.endsWith("links") }) {
                jsonResponse(
                    HalResource(_links = Links().apply {
                        add("health", "/health")
                    })
                )
            }

            rule({ path?.endsWith("health") }) {
                jsonResponse("""{"status":"FOOBAR","details":{"diskSpace":{"status":"UP"}}}""")
            }
        }

        val managementData = runBlocking {
            service.load(newPod {
                metadata = newObjectMeta {
                    name = "name2"
                    namespace = "namespace2"
                }
            }, ":8081/links")
        }
        assertThat(managementData).isNotNull()
        assertThat(managementData.health?.errorMessage).isEqualTo("Invalid format, status is not valid HealthStatus value")
    }

    @Test
    fun `management health should fail`() {
        httpMockServer(port) {
            rule({ path?.endsWith("links") }) {
                jsonResponse(
                    HalResource(_links = Links().apply {
                        add("health", "/health")
                    })
                )
            }

            rule({ path?.endsWith("health") }) {
                jsonResponse("""{"stastus":"UP","details":{"diskSpace":{"status":"UP"}}}""")
            }
        }

        val managementData = runBlocking {
            service.load(newPod {
                metadata = newObjectMeta {
                    name = "name3"
                    namespace = "namespace3"
                }
            }, ":8081/links")
        }

        assertThat(managementData).isNotNull()
        assertThat(managementData.health?.errorMessage).isEqualTo("Invalid format, does not contain status")
    }

    @Test
    fun `Request and create management data`() {
        httpMockServer(port) {
            rule({ path?.endsWith("links") }) {
                jsonResponse(
                    HalResource(_links = Links().apply {
                        add("info", "/info")
                        add("health", "/health")
                        add("env", "/env")
                    })
                )
            }

            rule({ path?.endsWith("info") }) {
                jsonResponse("{}")
            }

            rule({ path?.endsWith("env") }) {
                jsonResponse("""{"activeProfiles":["openshift"]}""")
            }

            rule({ path?.endsWith("health") }) {
                jsonResponse("""{"status":"UP","details":{"diskSpace":{"status":"UP"}}}""")
            }
        }

        val managementData = runBlocking {
            service.load(newPod {
                metadata = newObjectMeta {
                    name = "name4"
                    namespace = "namespace4"
                }
            }, ":8081/links")
        }

        assertThat(managementData).isNotNull()
        assertThat(managementData.env?.errorMessage).isNull()
        assertThat(managementData.health?.errorMessage).isNull()
        assertThat(managementData.info?.errorMessage).isNull()
    }

    @Test
    fun `Return ManagementData with error response for invalid endpoint configuration`() {
        val response = runBlocking {
            service.load(newPod {}, "invalid endpoint path")
        }

        assertThat(response.links.resultCode).isEqualTo("ERROR_CONFIGURATION")
    }
}
