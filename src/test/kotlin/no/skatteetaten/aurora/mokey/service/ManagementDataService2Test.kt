package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.httpMockServer
import no.skatteetaten.aurora.mockmvc.extensions.mockwebserver.jsonResponse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.web.reactive.function.client.WebClient
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class ManagementDataService2Test {

    private val port = 8282

    private val service = ManagementDataService(
        OpenShiftManagementClient(
            KubernetesReactorClient(
                WebClient.create("http://localhost:$port"), object : TokenFetcher {
                    override fun token() = "test-token"
                },
                RetryConfiguration()
            )
        )
    )

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
                jsonResponse("""{"activeProfiles":["openshift"]}""")
            }

            rule({ path?.endsWith("env") }) {
                jsonResponse("{}")
            }

            rule({ path?.endsWith("health") }) {
                jsonResponse("""{"status":"UP","details":{"diskSpace":{"status":"UP"}}}""")
            }
        }

        val managementData = service.load(newPod {
            metadata = newObjectMeta {
                name = "name"
                namespace = "namespace"
            }
        }, ":8081/links")

        assertThat(managementData).isNotNull()
        assertThat(managementData.env?.errorMessage).isNull()
        assertThat(managementData.health?.errorMessage).isNull()
        assertThat(managementData.info?.errorMessage).isNull()
    }
}
