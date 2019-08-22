package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@EnabledIfSystemProperty(named = "test.include-openshift-tests", matches = "true")
@SpringBootTest(
    classes = [WebClientConfig::class],
    properties = ["mokey.openshift.tokenLocation=file:/tmp/boober-token"]
)
class OpenShiftClientIntegrationTest @Autowired constructor(val openShiftClient: OpenShiftClient) {

    @Test
    fun `Get deployment config`() {
        val deploymentConfig = openShiftClient.dc("aurora", "boober").block()
        assertThat(deploymentConfig).isNotNull()
    }

    @Test
    fun `Get application deployment`() {
        val applicationDeployment = openShiftClient.ad("aurora", "boober").block()
        assertThat(applicationDeployment).isNotNull()
    }


}
