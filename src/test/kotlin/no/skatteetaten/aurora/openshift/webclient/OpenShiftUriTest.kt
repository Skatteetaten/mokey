package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class OpenShiftUriTest {

    @Test
    fun `Expand uri`() {
        val uri = OpenShiftUri("/test/{key}", mapOf("key" to "value", "blabla" to null))
        assertThat(uri.expand()).isEqualTo("/test/value")
    }
}
