package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.springframework.security.test.context.support.WithUserDetails

class AffiliationControllerTest : AbstractSecurityControllerTest() {

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @Test
    fun `Return list of affiliations`() {
        every { applicationDataService.findAllAffiliations() } returns listOf("paas", "affiliation1", "affiliation2")

        mockMvc.get(Path("/api/affiliation")) {
            statusIsOk().responseJsonPath("$.length()").equalsValue(3)
        }
    }

    @Test
    @WithUserDetails
    fun `Return list of visible affiliations`() {
        every { applicationDataService.findAllVisibleAffiliations() } returns listOf(
            "paas",
            "affiliation1",
            "affiliation2"
        )

        mockMvc.get(Path("/api/auth/affiliation")) {
            statusIsOk().responseJsonPath("$.length()").equalsValue(3)
        }
    }
}
