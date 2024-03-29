package no.skatteetaten.aurora.mokey.controller

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.openshift.newProject
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.mokey.StorageGridObjectAreaBuilder
import no.skatteetaten.aurora.mokey.controller.security.WebSecurityConfig
import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceOpenShift
import no.skatteetaten.aurora.mokey.service.OpenShiftServiceAccountClient
import no.skatteetaten.aurora.mokey.service.OpenShiftUserClient
import no.skatteetaten.aurora.mokey.service.StorageGridObjectAreaService
import no.skatteetaten.aurora.springboot.AuroraSecurityContextRepository
import no.skatteetaten.aurora.springboot.OpenShiftAuthenticationManager
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.TestStubSetup
import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.get
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.security.test.context.support.WithMockUser

@Suppress("unused")
@WithMockUser("test", roles = ["test"])
@WebFluxTest(
    WebSecurityConfig::class,
    StorageGridObjectAreaController::class,
    StorageGridObjectAreaService::class,
)
class StorageGridObjectAreaControllerTest : TestStubSetup() {

    @MockkBean
    private lateinit var openShiftAuthenticationManager: OpenShiftAuthenticationManager

    @MockkBean
    private lateinit var securityContextRepository: AuroraSecurityContextRepository

    @MockkBean
    private lateinit var userClient: OpenShiftUserClient

    @MockkBean
    private lateinit var applicationDataService: ApplicationDataServiceOpenShift

    @MockkBean
    private lateinit var openShiftServiceAccountClient: OpenShiftServiceAccountClient

    @Autowired
    private lateinit var sgoaService: StorageGridObjectAreaService

    @BeforeEach
    fun setup() {
        coEvery { openShiftServiceAccountClient.getProjectsInAffiliation(any()) } answers {
            listOf(
                newProject {
                    metadata = newObjectMeta {
                        name = firstArg()
                        labels = mapOf("affiliation" to firstArg())
                    }
                }
            )
        }

        coEvery { openShiftServiceAccountClient.getStorageGridObjectAreas(any()) } answers {
            listOf(StorageGridObjectAreaBuilder(namespace = firstArg()).build())
        }
    }

    @Test
    fun `Return StorageGridObjectAreas`() {
        coEvery { userClient.getProjectsInAffiliation(any()) } returns listOf(
            newProject {
                metadata = newObjectMeta {
                    name = "aup"
                    labels = mapOf("affiliation" to "aup")
                }
            }
        )

        runBlocking {
            sgoaService.refreshCache(mapOf("aup" to emptyList()))
        }

        webTestClient
            .get("/api/auth/storagegridobjectarea?affiliation=aup") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.[0].objectArea").isEqualTo("referanse-java2")
            }
    }

    @Test
    fun `Returns only StorageGridObjectAreas user has access to`() {
        coEvery { userClient.getProjectsInAffiliation(any()) } returns emptyList()
        coEvery { userClient.getProjectsInAffiliation("foo") } returns listOf(
            newProject {
                metadata = newObjectMeta {
                    name = "foo"
                    labels = mapOf("affiliation" to "foo")
                }
            }
        )

        runBlocking {
            sgoaService.refreshCache(
                mapOf(
                    "aup" to emptyList(),
                    "foo" to emptyList()
                )
            )
        }

        webTestClient
            .get("/api/auth/storagegridobjectarea?affiliation=aup") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .json("[]")
            }

        webTestClient
            .get("/api/auth/storagegridobjectarea?affiliation=foo") {
                exchange()
                    .expectStatus()
                    .isOk
                    .expectBody()
                    .jsonPath("$.[0].objectArea").isEqualTo("referanse-java2")
            }
    }
}
