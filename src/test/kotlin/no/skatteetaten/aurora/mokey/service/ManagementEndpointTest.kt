package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.ApplicationConfig
import org.junit.Test
import org.springframework.boot.web.client.RestTemplateBuilder

class ManagementEndpointTest {

    @Test
    fun `a`() {
        val restTemplate = ApplicationConfig().restTemplate(RestTemplateBuilder())
        val managementEndpoint = ManagementEndpoint.create(restTemplate, "http://localhost:8081/actuator")
        managementEndpoint.getHealthEndpointResponse().let(::println)
        managementEndpoint.getInfoEndpointResponse().let(::println)
        managementEndpoint.links.let(::println)
    }
}
