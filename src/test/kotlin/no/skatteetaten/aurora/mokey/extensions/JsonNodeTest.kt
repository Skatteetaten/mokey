package no.skatteetaten.aurora.mokey.extensions

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test

class JsonNodeTest {
    private val mapper = jacksonObjectMapper()

    @Test
    fun `it should extract value for a given key`() {
        val git = mapper.readTree(
            """{
            |"commit.time": "03.04.2019 @ 16:30:27 CEST",
            |"commit.id.short":"jd342h4"
            |}""".trimMargin()
        )

        val id = git.extract("/commit.id.abbrev", "/commit/id", "/commit.id.short")
        assertThat(id?.asText()).isEqualTo("jd342h4")

        val time = git.extract("/commit.time/v1", "/commit.time", "/commit/time")
        assertThat(time?.asText()).isEqualTo("03.04.2019 @ 16:30:27 CEST")
    }

    @Test
    fun `it should extract value for keys with exact match`() {
        val git = mapper.readTree(
            """{
            |"commit.time/v1": "03.04.2019 @ 16:30:27 CEST"
            |}""".trimMargin()
        )

        val time = git.extract("/commit.time/v1")
        assertThat(time?.asText()).isEqualTo("03.04.2019 @ 16:30:27 CEST")
    }
}