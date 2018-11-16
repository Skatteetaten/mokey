package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class ManagementLinksTest {

    @Test
    fun `should parse management links response correctly`() {
        @Language("JSON")
        val json = """{
  "_links": {
    "health": {
      "href": "http://localhost:8081/health"
    },
    "env": {
      "href": "http://localhost:8081/env"
    },
    "info": {
      "href": "http://localhost:8081/info"
    }
  }
}"""
        val managementLinks = ManagementLinks.parseManagementResponse(jacksonObjectMapper().readValue(json))

        assertThat(managementLinks.linkFor(EndpointType.HEALTH)).isEqualTo("http://localhost:8081/health")
        assertThat(managementLinks.linkFor(EndpointType.INFO)).isEqualTo("http://localhost:8081/info")
    }
}