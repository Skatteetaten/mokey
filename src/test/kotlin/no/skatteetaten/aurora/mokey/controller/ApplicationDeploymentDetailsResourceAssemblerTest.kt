package no.skatteetaten.aurora.mokey.controller

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import no.skatteetaten.aurora.mokey.ApplicationDataBuilder
import org.junit.jupiter.api.Test

@ExperimentalStdlibApi
class ApplicationDeploymentDetailsResourceAssemblerTest {

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
