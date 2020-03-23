package no.skatteetaten.aurora.mokey

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.fail
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentsWithDbResource
import no.skatteetaten.aurora.mokey.controller.ApplicationResource
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.io.File

const val affiliation = "aurora"
const val oldMokeyUrl = "http://mokey-aurora.utv.paas.skead.no"
const val newMokeyUrl = "http://localhost:8080"
const val dbId = "a0320050-a2fd-4f74-9923-87f07d7f4755" // Find a db that is used by an application

@Disabled
class MokeyApiTester {

    private val old = createWebClient(oldMokeyUrl)
    private val new = createWebClient(newMokeyUrl)

    private val token = kubernetesToken()

    @Test
    fun `Get application resources`() {
        val applications1 = old.getApplications(affiliation)
        val applications2 = new.getApplications(affiliation)
        applications1.isEqualToApplications(applications2)
    }

    @Test
    fun `Get deployment details`() {
        val deploymentDetails1 = old.getApplicationDeploymentDetails(affiliation)
        val deploymentDetails2 = new.getApplicationDeploymentDetails(affiliation)
        deploymentDetails1.isEqualToDetails(deploymentDetails2)
    }

    @Test
    fun `Get application deployment for db`() {
        val deployments1 = old.getApplicationDeploymentForDb(dbId)
        val deployments2 = new.getApplicationDeploymentForDb(dbId)
        deployments1.isEqualToDeploymentDb(deployments2)
    }

    private fun List<ApplicationResource>.isEqualToApplications(applications: List<ApplicationResource>) {
        this.forEach { resource ->
            applications.find { it.identifier == resource.identifier }?.let {
                assertThat(it.name).isEqualTo(resource.name)
                assertThat(it.applicationDeployments.size).isEqualTo(resource.applicationDeployments.size)
                it.applicationDeployments.isEqualToDeployments(resource.applicationDeployments)
            } ?: fail("No ApplicationResource found for ${resource.identifier}")
        }
    }

    private fun List<ApplicationDeploymentResource>.isEqualToDeployments(deployments: List<ApplicationDeploymentResource>) {
        this.forEach { resource ->
            deployments.find { it.identifier == resource.identifier }?.let {
                assertThat(it.affiliation).isEqualTo(resource.affiliation)
                // assertThat(it.dockerImageRepo).isEqualTo(resource.dockerImageRepo)
                assertThat(it.environment).isEqualTo(resource.environment)
                assertThat(it.message).isEqualTo(resource.message)
                assertThat(it.name).isEqualTo(resource.name)
                assertThat(it.namespace).isEqualTo(resource.namespace)
                assertThat(it.status.code).isEqualTo(resource.status.code)
                // assertThat(it.version.auroraVersion).isEqualTo(resource.version.auroraVersion)
            } ?: fail("No ApplicationDeploymentResource found for ${resource.identifier}")
        }
    }

    private fun List<ApplicationDeploymentDetailsResource>.isEqualToDetails(details: List<ApplicationDeploymentDetailsResource>) {
        this.forEach { resource ->
            details.find { it.identifier == resource.identifier }?.let {
                assertThat(it.updatedBy).isEqualTo(resource.updatedBy)
                // assertThat(it.buildTime).isEqualTo(resource.buildTime)
            } ?: fail("No ApplicationDeploymentDetailsResource found for ${resource.identifier}")
        }
    }

    private fun List<ApplicationDeploymentsWithDbResource>.isEqualToDeploymentDb(dbResources: List<ApplicationDeploymentsWithDbResource>) {
        this.forEach { resource ->
            dbResources.find { it.identifier == resource.identifier }?.let {
                assertThat(it.applicationDeployments.size).isEqualTo(resource.applicationDeployments.size)
            } ?: fail("No ApplicationDeploymentsWithDbResource found for ${resource.identifier}")
        }
    }

    private fun WebClient.getApplications(affiliation: String) =
        this.get()
            .uri {
                it.path("/api/application")
                    .queryParam("affiliation", affiliation)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .bodyToMono<List<ApplicationResource>>()
            .block()!!

    private fun WebClient.getApplicationDeploymentDetails(affiliation: String) =
        this.get()
            .uri {
                it.path("/api/auth/applicationdeploymentdetails")
                    .queryParam("affiliation", affiliation)
                    .build()
            }
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .retrieve()
            .bodyToMono<List<ApplicationDeploymentDetailsResource>>()
            .block()!!

    private fun WebClient.getApplicationDeploymentForDb(id: String) =
        this.post()
            .uri("/api/auth/applicationdeploymentbyresource/databases")
            .body(BodyInserters.fromValue("""["$id"]"""))
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .bodyToMono<List<ApplicationDeploymentsWithDbResource>>()
            .block()!!

    private fun createWebClient(baseUrl: String) =
        WebClient.builder().exchangeStrategies(ExchangeStrategies.builder().codecs {
            it.defaultCodecs().apply {
                maxInMemorySize(-1) // unlimited
            }
        }.build()).baseUrl(baseUrl).build()

    private fun kubernetesToken(environment: String = "utv-master"): String {
        val kubernetesConfig = File("${System.getProperty("user.home")}/.kube/config")
        if (!kubernetesConfig.exists())
            throw IllegalArgumentException("No kubernetes config file found in ${kubernetesConfig.absolutePath}")

        val content = kubernetesConfig.readText()
        val values = ObjectMapper(YAMLFactory()).readTree(content)
        return values.at("/users").iterator().asSequence()
            .firstOrNull { it.at("/name").textValue().contains("$environment-paas-skead-no") }
            ?.at("/user/token")?.textValue()
            ?: throw IllegalArgumentException("No Kubernetes token found for environment $environment")
    }
}
