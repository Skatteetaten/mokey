package no.skatteetaten.aurora.mokey.model

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HttpResponseTest {

    @Test
    fun `Return empty string for non-json response`() {
        val html = "<html></html>"
        val response = HttpResponse(html, 200)
        assertThat(response.jsonContentOrError()).contains(html)
    }

    @Test
    fun `Return string for json response`() {
        val json = """{ "key":"value" }"""
        val response = HttpResponse(json, 200)
        assertThat(response.jsonContentOrError()).isEqualTo(json)
    }
}