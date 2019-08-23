package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.catch
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewResourceAttributes
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

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

    @Test
    fun `Get projects with invalid token`() {
        val exception = catch { openShiftClient.projects(token = "abc123").block() }
        assertThat((exception as WebClientResponseException).statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `Get project`() {
        val project = openShiftClient.project("aurora").block()
        assertThat(project).isNotNull()
    }

    @Test
    fun `Post self subject review access`() {
        val review = SelfSubjectAccessReview(
            spec = SelfSubjectAccessReviewSpec(
                resourceAttributes = SelfSubjectAccessReviewResourceAttributes(
                    namespace = "aurora",
                    verb = "update",
                    resource = "deploymentconfigs"
                )
            )
        )

        val selfSubjectAccessReview = openShiftClient.selfSubjectAccessView(review).block()
        assertThat(selfSubjectAccessReview).isNotNull()
    }
}
