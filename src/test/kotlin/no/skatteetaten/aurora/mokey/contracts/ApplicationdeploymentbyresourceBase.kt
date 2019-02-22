package no.skatteetaten.aurora.mokey.contracts

import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.CacheWarmup
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentsWithDbResourceAssembler
import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.junit.jupiter.api.BeforeEach
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.context.TestPropertySource

@WithUserDetails
@TestPropertySource(properties = ["mokey.cache.enabled=true"])
open class ApplicationdeploymentbyresourceBase : ContractBase() {

    @MockBean(name = "ApplicationDataServiceCacheDecorator")
    private lateinit var applicationDataService: ApplicationDataServiceCacheDecorator

    @MockBean
    private lateinit var assembler: ApplicationDeploymentsWithDbResourceAssembler

    @MockBean
    private lateinit var cacheWarmup: CacheWarmup

    @BeforeEach
    fun setUp() {
        withContractResponses(this) {
            val applicationData = ApplicationDataBuilder().build()
            given(applicationDataService.getAllApplicationDataFromCache()).willReturn(
                listOf(applicationData)
            )

            given(assembler.toResources(any())).willReturn(it.response())
        }
    }
}