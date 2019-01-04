package no.skatteetaten.aurora.mokey.contracts

import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.controller.ApplicationResourceAssembler
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.BeforeEach
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean

open class ApplicationBase : ContractBase() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationResourceAssembler

    @BeforeEach
    fun setUp() {
        withContractResponses(this) {
            val applicationData = ApplicationDataBuilder().build()

            given(applicationDataService.findAllPublicApplicationDataByApplicationId(any())).willReturn(
                listOf(applicationData.publicData)
            )
            given(applicationDataService.findAllPublicApplicationData(any(), any())).willReturn(
                listOf(applicationData.publicData)
            )

            given(assembler.toResource(any())).willReturn(it.response("application"))
            given(assembler.toResources(any())).willReturn(it.response("applications"))
        }
    }
}