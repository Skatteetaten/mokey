package no.skatteetaten.aurora.mokey.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.skatteetaten.aurora.mockmvc.extensions.Path
import no.skatteetaten.aurora.mockmvc.extensions.get
import no.skatteetaten.aurora.mockmvc.extensions.responseJsonPath
import no.skatteetaten.aurora.mockmvc.extensions.statusIsOk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import no.skatteetaten.aurora.mokey.ApplicationResourceBuilder
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.junit.jupiter.api.Test

class ApplicationControllerTest : AbstractSecurityControllerTest() {

    @MockkBean(relaxed = true)
    private lateinit var applicationDataService: ApplicationDataService

    @MockkBean
    private lateinit var assembler: ApplicationResourceAssembler

    private val applicationData = ApplicationDataBuilder().build()

    @Test
    fun `Return application by id`() {
        every { applicationDataService.findAllPublicApplicationDataByApplicationId(any()) } returns listOf(applicationData.publicData)
        every { assembler.toResource(any()) } returns ApplicationResourceBuilder().build()

        mockMvc.get(Path("/api/application/{id}", "abc123")) {
            statusIsOk().responseJsonPath("$.identifier").equalsValue("123")
        }
    }

    @Test
    fun `Return applications for affiliation`() {
        every { applicationDataService.findAllPublicApplicationData(any(), any()) } returns listOf(applicationData.publicData)
        every { assembler.toResources(any()) } returns listOf(ApplicationResourceBuilder().build())

        mockMvc.get(Path("/api/application?affiliation=paas")) {
            statusIsOk().responseJsonPath("$.length()").equalsValue(1)
        }
    }
}
