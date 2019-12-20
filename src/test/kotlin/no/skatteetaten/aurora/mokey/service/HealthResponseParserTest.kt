package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.databind.node.IntNode
import com.fasterxml.jackson.databind.node.LongNode
import com.fasterxml.jackson.databind.node.TextNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.HealthPart
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test

class HealthResponseParserTest {

    @Test
    fun `parse flat spring-boot 1-X health endpoint response`() {
        @Language("JSON")
        val json = """{
  "status": "UP",
  "atsServiceHelse": {
    "status": "UP"
  },
  "diskSpace": {
    "status": "UP",
    "total": 10718543872,
    "free": 10508611584,
    "threshold": 10485760
  },
  "db": {
    "status": "UP",
    "database": "Oracle",
    "hello": "Hello"
  }
}"""

        val response = parse(json)
        val expected = HealthResponse(
            status = HealthStatus.UP,
            parts = mutableMapOf(
                "atsServiceHelse" to HealthPart(
                    status = HealthStatus.UP,
                    details = mutableMapOf()
                ),
                "diskSpace" to HealthPart(
                    status = HealthStatus.UP,
                    details = mutableMapOf(
                        "total" to 10718543872.node(),
                        "threshold" to 10485760.node(),
                        "free" to 10508611584.node()
                    )
                ),
                "db" to HealthPart(
                    status = HealthStatus.UP,
                    details = mutableMapOf(
                        "hello" to "Hello".node(),
                        "database" to "Oracle".node()
                    )
                )
            )
        )
        assertThat(response).isEqualTo(expected)
    }

    @Test
    fun `parse nested spring-boot 2-X health endpoint response`() {
        @Language("JSON")
        val json = """{
  "status": "UP",
  "details": {
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 10718543872,
        "free": 10502053888,
        "threshold": 10485760
      }
    },
    "db": {
      "status": "UP",
      "details": {
        "database": "Oracle",
        "hello": "Hello"
      }
    }
  }
}"""

        val response = parse(json)
        val expected = HealthResponse(
            status = HealthStatus.UP,
            parts = mutableMapOf(
                "diskSpace" to HealthPart(
                    status = HealthStatus.UP,
                    details = mutableMapOf(
                        "total" to 10718543872.node(),
                        "threshold" to 10485760.node(),
                        "free" to 10502053888.node()
                    )
                ),
                "db" to HealthPart(
                    status = HealthStatus.UP,
                    details = mutableMapOf(
                        "hello" to "Hello".node(),
                        "database" to "Oracle".node()
                    )
                )
            )
        )
        assertThat(response).isEqualTo(expected)
    }

    private fun String.node() = TextNode.valueOf(this)!!

    private fun Int.node() = IntNode.valueOf(this)!!

    private fun Long.node() = LongNode.valueOf(this)!!

    private fun parse(json: String) = HealthResponseParser.parse(jacksonObjectMapper().readTree(json))
}
