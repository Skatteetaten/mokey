package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.clearMocks
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.contentTypeJson
import no.skatteetaten.aurora.mockmvc.extensions.post
import no.skatteetaten.aurora.mockmvc.extensions.status
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.security.test.context.support.WithUserDetails

@WithUserDetails
class RefreshCacheControllerTest : AbstractSecurityControllerTest() {

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @BeforeEach
    override fun setUp() {
        clearMocks(applicationDataService)
    }

    @Test
    fun `Refresh cache with applicationDeploymentId`() {
        mockMvc.post(
            path = Path("/api/auth/refresh"),
            body = RefreshParams(applicationDeploymentId = "123", affiliations = null),
            headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk()
        }
    }

    @Test
    fun `Refresh cache with unknown applicationDeploymentId`() {
        every { applicationDataService.refreshItem(any()) } throws IllegalArgumentException("test exception")
        mockMvc.post(
            path = Path("/api/auth/refresh"),
            body = RefreshParams(applicationDeploymentId = "123", affiliations = null),
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `Refresh cache with affiliations`() {
        mockMvc.post(
            path = Path("/api/auth/refresh"),
            body = RefreshParams(applicationDeploymentId = null, affiliations = listOf("aurora")),
            headers = HttpHeaders().contentTypeJson()
        ) {
            statusIsOk()
        }
    }

    @Test
    fun `Refresh cache missing input`() {
        mockMvc.post(
            path = Path("/api/auth/refresh"),
            body = RefreshParams(applicationDeploymentId = null, affiliations = null),
            headers = HttpHeaders().contentTypeJson()
        ) {
            status(HttpStatus.BAD_REQUEST)
        }
    }
}
