package no.skatteetaten.aurora.mokey.controller

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

@ExperimentalStdlibApi
class ApplicationDeploymentDetailsResourceAssemblerTest {
    @BeforeEach
    fun setUp() {
        val request = MockHttpServletRequest()
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(request))
    }

    @Test
    fun `Get ApplicationDeploymentDetailsResource with metrics link`() {
        val assembler = ApplicationDeploymentDetailsResourceAssembler(
            LinkBuilder(
                "http://boober/api",
                mapOf("metricsHostname" to "http://metrics")
            )
        )

        val resource = assembler.toResource(ApplicationDataBuilder().build())
        assertThat(resource).isNotNull()

        val links = resource.podResources.first()._links
        assertThat(links.link("test").href).isEqualTo("http://localhost")
        assertThat(links.link("metrics").href).isEqualTo("http://metrics")
    }
}
