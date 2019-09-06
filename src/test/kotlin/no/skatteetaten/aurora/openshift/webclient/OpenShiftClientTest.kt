package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.net.URI

class OpenShiftClientTest {

    @Test
    fun `Get requested OpenShift resource`() {
        val resource = URI
            .create("https://localhost:8443/apis/apps.openshift.io/v1/namespaces/my-namespace/deploymentconfigs/my-project")
            .requestedOpenShiftResource()
        assertThat(resource?.namespace).isEqualTo("my-namespace")
        assertThat(resource?.kind).isEqualTo("deploymentconfigs")
        assertThat(resource?.name).isEqualTo("my-project")
    }

    @Test
    fun `Get requested OpenShift resource with invalid url`() {
        val resource = URI
            .create("https://localhost:8443/apis/apps.openshift.io/v1")
            .requestedOpenShiftResource()
        assertThat(resource).isNull()
    }
}