package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.catch
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewResourceAttributes
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReviewSpec
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

@EnabledIfSystemProperty(named = "test.include-openshift-tests", matches = "true")
@AutoConfigureWebClient
@SpringBootTest(
    classes = [OpenShiftClientConfig::class],
    properties = ["mokey.openshift.tokenLocation=file:/tmp/boober-token"]
)
class OpenShiftClientIntegrationTest @Autowired constructor(private val client: OpenShiftClient) {

    @Value("\${mokey.openshift.tokenLocation}")
    private lateinit var token: Resource

    @Test
    fun `Get deployment config`() {
        val deploymentConfig = client.serviceAccount().deploymentConfig("aurora", "boober").block()
        assertThat(deploymentConfig).isNotNull()
    }

    @Test
    fun `Get application deployment`() {
        val applicationDeployment = client.serviceAccount().applicationDeployment("aurora", "boober").block()
        assertThat(applicationDeployment).isNotNull()
    }

    @Test
    fun `Get application deployments`() {
        val applicationDeployments = client.serviceAccount().applicationDeployments("aurora").block()
        assertThat(applicationDeployments).isNotNull()
    }

    @Test
    fun `Get route`() {
        val route = client.serviceAccount().route("aurora", "boober").block()
        assertThat(route).isNotNull()
    }

    @Test
    fun `Get routes`() {
        val routes = client.serviceAccount().routes("aurora", mapOf("app" to "argus")).block()
        assertThat(routes).isNotNull()
    }

    @Test
    fun `Get services`() {
        val services = client.serviceAccount().services("aurora", mapOf("app" to "console")).block()
        assertThat(services).isNotNull()
    }

    @Test
    fun `Get pods`() {
        val pods = client.serviceAccount().pods("aurora", mapOf("name" to "cantus")).block()
        assertThat(pods).isNotNull()
    }

    @Test
    fun `Get replication controller`() {
        val deploymentConfig = client.serviceAccount().deploymentConfig("aurora", "argus").block()
        val replicationController = client.serviceAccount().replicationController(
            "aurora",
            "argus-${deploymentConfig?.status?.latestVersion}"
        ).block()
        assertThat(replicationController).isNotNull()
    }

    @Test
    fun `Get imagestream tag`() {
        val imageStreamTag = client.serviceAccount().imageStreamTag("aurora", "console", "default").block()
        assertThat(imageStreamTag).isNotNull()
    }

    @Test
    fun `Get imagestream tag not found`() {
        val imageStreamTag = client.serviceAccount().imageStreamTag("aurora", "referanse", "default")
            .blockForResource()
        assertThat(imageStreamTag).isNull()
    }

    @Test
    fun `Get projects`() {
        val projects = client.serviceAccount().projects().block()
        assertThat(projects).isNotNull()
    }

    @Test
    fun `Get projects with token`() {
        val projects = client.userToken(token.readContent()).projects().block()
        assertThat(projects).isNotNull()
    }

    @Test
    fun `Get projects with invalid token`() {
        val exception = catch { client.userToken("abc123").projects().blockForResource() }
        assertThat((exception as WebClientResponseException).statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `Get project`() {
        val project = client.serviceAccount().project("aurora").block()
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

        val selfSubjectAccessReview = client.serviceAccount().selfSubjectAccessView(review).block()
        assertThat(selfSubjectAccessReview).isNotNull()
    }

    @Test
    fun `Get user`() {
        val user = client.userToken(token.readContent()).user().block()
        assertThat(user).isNotNull()
    }
}
