package no.skatteetaten.aurora.mokey.controller

import com.nhaarman.mockito_kotlin.any
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.mock.withContractResponse
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.springframework.boot.test.mock.mockito.MockBean

class ApplicationControllerTest : AbstractSecurityControllerTest() {

    @MockBean
    private lateinit var applicationDataService: ApplicationDataService

    @MockBean
    private lateinit var assembler: ApplicationResourceAssembler

    private val applicationData = ApplicationDataBuilder().build()

    @Test
    fun `Return application by id`() {
        given(applicationDataService.findAllPublicApplicationDataByApplicationId(any())).willReturn(
            listOf(applicationData.publicData)
        )

        val application = given(assembler.toResource(any()))
            .withContractResponse(name = "application/application") { willReturn(content) }.mockResponse

        mockMvc.get(Path("/api/application/{id}", "abc123")) {
            statusIsOk().responseJsonPath("$").equalsObject(expected = application)
        }
    }

    @Test
    fun `Return applications for affiliation`() {
        given(applicationDataService.findAllPublicApplicationData(any(), any())).willReturn(
            listOf(applicationData.publicData)
        )

        mockMvc.get(Path("/api/application?affiliation=paas")) {
            statusIsOk().responseJsonPath("$.length()").equalsValue(1)
        }
    }
}
