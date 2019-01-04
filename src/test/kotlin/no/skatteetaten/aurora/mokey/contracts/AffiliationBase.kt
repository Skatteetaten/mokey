package no.skatteetaten.aurora.mokey.contracts

import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.BeforeEach
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithMockUser

@WithMockUser
open class AffiliationBase : ContractBase() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @BeforeEach
    fun setUp() {
        withContractResponses(this) {
            given(applicationDataService.findAllAffiliations()).willReturn(it.response())
            given(applicationDataService.findAllVisibleAffiliations()).willReturn(it.response())
        }
    }
}