package no.skatteetaten.aurora.mokey.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class HttpResponseTest {

    @Test
    fun `Return empty string for non-json response`() {
        val response = HttpResponse("<html></html>", 200)
        assertThat(response.jsonContentOrErrorMsg()).isEqualTo(jsonFormatErrorMsg)
    }

    @Test
    fun `Return string for json response`() {
        val json = """{ "key":"value" }"""
        val response = HttpResponse(json, 200)
        assertThat(response.jsonContentOrErrorMsg()).isEqualTo(json)
    }
}