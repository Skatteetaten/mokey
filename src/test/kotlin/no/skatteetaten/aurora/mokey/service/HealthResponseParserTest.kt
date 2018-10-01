package no.skatteetaten.aurora.mokey.service

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
        assertk.assert(response).isEqualTo(
            HealthResponse(
                HealthStatus.UP,
                mutableMapOf(
                    "atsServiceHelse" to HealthPart(HealthStatus.UP, mutableMapOf()),
                    "diskSpace" to HealthPart(
                        HealthStatus.UP, mutableMapOf(
                            "total" to LongNode.valueOf(10718543872),
                            "threshold" to IntNode.valueOf(10485760),
                            "free" to LongNode.valueOf(10508611584)
                        )
                    ),
                    "db" to HealthPart(
                        HealthStatus.UP, mutableMapOf(
                            "hello" to TextNode.valueOf("Hello"),
                            "database" to TextNode.valueOf("Oracle")
                        )
                    )
                )
            )
        )
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
        assertk.assert(response).isEqualTo(
            HealthResponse(
                HealthStatus.UP,
                mutableMapOf(
                    "diskSpace" to HealthPart(
                        HealthStatus.UP, mutableMapOf(
                            "total" to LongNode.valueOf(10718543872),
                            "threshold" to IntNode.valueOf(10485760),
                            "free" to LongNode.valueOf(10502053888)
                        )
                    ),
                    "db" to HealthPart(
                        HealthStatus.UP, mutableMapOf(
                            "hello" to TextNode.valueOf("Hello"),
                            "database" to TextNode.valueOf("Oracle")
                        )
                    )
                )
            )
        )
    }

    private fun parse(json: String) = HealthResponseParser.parse(jacksonObjectMapper().readTree(json))
}