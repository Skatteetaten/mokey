package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.mock.withContractResponse
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails

class AffiliationControllerTest : AbstractSecurityControllerTest() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @Test
    fun `Return list of affiliations`() {
        given(applicationDataService.findAllAffiliations())
            .withContractResponse("affiliation/affiliations") { willReturn(content) }

        mockMvc.get(Path("/api/affiliation")) {
            statusIsOk().responseJsonPath("$.length()").equalsValue(3)
        }
    }

    @Test
    @WithUserDetails
    fun `Return list of visible affiliations`() {
        given(applicationDataService.findAllVisibleAffiliations())
            .withContractResponse("affiliation/affiliations") { willReturn(content) }

        mockMvc.get(Path("/api/auth/affiliation")) {
            statusIsOk().responseJsonPath("$.length()").equalsValue(3)
        }
    }
}