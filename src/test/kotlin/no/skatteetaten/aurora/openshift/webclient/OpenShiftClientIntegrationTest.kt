package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.context.SpringBootTest

@EnabledIfSystemProperty(named = "test.include-openshift-tests", matches = "true")
@AutoConfigureWebClient
@SpringBootTest(
    classes = [WebClientConfig::class],
    properties = ["mokey.openshift.tokenLocation=file:/tmp/boober-token"]
)
class OpenShiftClientIntegrationTest @Autowired constructor(val openShiftClient: OpenShiftClient) {

    @Test
    fun `Get deployment config`() {
        val deploymentConfig = openShiftClient.deploymentConfig("aurora", "boober").block()
        assertThat(deploymentConfig).isNotNull()
    }

    @Test
    fun `Get application deployment`() {
        val applicationDeployment = openShiftClient.applicationDeployment("aurora", "boober").block()
        assertThat(applicationDeployment).isNotNull()
    }

    @Test
    fun `Get application deployments`() {
        val applicationDeployments = openShiftClient.applicationDeployments("aurora").block()
        assertThat(applicationDeployments).isNotNull()
    }

    @Test
    fun `Get route`() {
        val route = openShiftClient.route("aurora", "boober").block()
        assertThat(route).isNotNull()
    }

    @Test
    fun `Get routes`() {
        val routes = openShiftClient.routes("aurora", mapOf("app" to "argus")).block()
        assertThat(routes).isNotNull()
    }

    @Test
    fun `Get services`() {
        val services = openShiftClient.services("aurora", mapOf("app" to "console")).block()
        assertThat(services).isNotNull()
    }

    @Test
    fun `Get pods`() {
        val pods = openShiftClient.pods("aurora", mapOf("name" to "cantus")).block()
        assertThat(pods).isNotNull()
    }

    @Test
    fun `Get replication controller`() {
        val replicationController = openShiftClient.replicationController("aurora", "cantus-55").block()
        assertThat(replicationController).isNotNull()
    }

    @Test
    fun `Get imagestream tag`() {
        val imageStreamTag = openShiftClient.imageStreamTag("aurora", "console", "default").block()
        assertThat(imageStreamTag).isNotNull()
    }

    @Test
    fun `Get projects`() {
        val projects = openShiftClient.projects().block()
        assertThat(projects).isNotNull()
    }
}
