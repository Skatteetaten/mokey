package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows

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

        assertThat(managementLinks.linkFor(Endpoint.HEALTH)).isEqualTo("http://localhost:8081/health")
        assertThat(managementLinks.linkFor(Endpoint.INFO)).isEqualTo("http://localhost:8081/info")
    }

    @Test
    fun `should fail when link is missing`() {
        @Language("JSON")
        val json = """{
  "_links": {
    "env": {
      "href": "http://localhost:8081/env"
    },
    "info": {
      "href": "http://localhost:8081/info"
    }
  }
}"""
        val managementLinks = ManagementLinks.parseManagementResponse(jacksonObjectMapper().readValue(json))

        val e = assertThrows(ManagementEndpointException::class.java) {
            managementLinks.linkFor(Endpoint.HEALTH)
        }
        assertThat(e.endpoint).isEqualTo(Endpoint.HEALTH)
        assertThat(e.errorCode).isEqualTo("LINK_MISSING")
    }
}