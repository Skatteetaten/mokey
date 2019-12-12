package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.util.UriComponentsBuilder

@Configuration
class LinkBuilderFactory(
    @Value("\${integrations.boober.url}") val booberApiUrl: String,
    @Value("\${integrations.metrics.url}") val metricsHostname: String,
    @Value("\${integrations.splunk.url}") val splunkHostname: String,
    @Value("\${openshift.cluster}") val cluster: String
) {
    @Bean
    fun linkBuilder(): LinkBuilder = LinkBuilder(booberApiUrl, expandParams)
}

class LinkBuilder(private val booberApiUrl: String, private val globalExpandParams: Map<String, String>) {

    fun applyResult(auroraConfigName: String, deployId: String) =
        createLink(
            UriComponentsBuilder
                .fromHttpUrl(booberApiUrl)
                .path("/v1/apply-result/$auroraConfigName/$deployId")
                .build().toUriString(), "ApplyResult"
        )

    fun apply(deploymentCommand: ApplicationDeploymentCommand): Pair<String, String> {
        val uriComponents = UriComponentsBuilder.fromHttpUrl(booberApiUrl)
            .pathSegment("v1", "apply", deploymentCommand.auroraConfig.name)
            .queryParam("reference", deploymentCommand.auroraConfig.refName).build()
            .encode()
            .toUriString()

        return createLink(uriComponents, "Apply")
    }

    fun auroraConfigFile(deploymentCommand: ApplicationDeploymentCommand): Map<String, String> {

        val uriComponents = UriComponentsBuilder.fromHttpUrl(booberApiUrl)
            .pathSegment("v1", "auroraconfig", deploymentCommand.auroraConfig.name, "{fileName}")

        val currentLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.refName).build()
                .encode()
                .toUriString()
        val deployedLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.resolvedRef).build()
                .encode()
                .toUriString()

        return mapOf(
            createLink(currentLink, "AuroraConfigFileCurrent"),
            createLink(deployedLink, "AuroraConfigFileDeployed")
        )
    }

    fun files(deploymentCommand: ApplicationDeploymentCommand): Map<String, String> {

        val uriComponents = UriComponentsBuilder.fromHttpUrl(booberApiUrl)
            .pathSegment("v1", "auroraconfig", deploymentCommand.auroraConfig.name)
            .queryParam("environment", deploymentCommand.applicationDeploymentRef.environment)
            .queryParam("application", deploymentCommand.applicationDeploymentRef.application)

        val currentLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.refName).build()
                .encode()
                .toUriString()
        val deployedLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.resolvedRef).build()
                .encode()
                .toUriString()

        return mapOf(
            createLink(currentLink, "FilesCurrent"),
            createLink(deployedLink, "FilesDeployed")
        )
    }

    fun deploymentSpec(deploymentCommand: ApplicationDeploymentCommand): Map<String, String> {
        val overridesQueryParam = deploymentCommand.overrideFiles.takeIf { it.isNotEmpty() }?.let {
            jacksonObjectMapper().writeValueAsString(it)
        }

        val uriComponents = UriComponentsBuilder.fromHttpUrl(booberApiUrl)
            .pathSegment(
                "v1",
                "auroradeployspec",
                deploymentCommand.auroraConfig.name,
                deploymentCommand.applicationDeploymentRef.environment,
                deploymentCommand.applicationDeploymentRef.application
            )

        overridesQueryParam?.let {
            uriComponents.queryParam("overrides", it)
        }

        val currentLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.refName).build()
                .encode()
                .toUriString()
        val deployedLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.resolvedRef).build()
                .encode()
                .toUriString()

        return mapOf(
            createLink(currentLink, "DeploymentSpecCurrent"),
            createLink(deployedLink, "DeploymentSpecDeployed")
        )
    }

    // TODO: Should improve the way we create deep links to pods in the console.
    fun openShiftConsoleLinks(pod: String, project: String): Map<String, String> {
        return listOf("details", "environment", "terminal", "events", "log").map {
            val url = UriComponentsBuilder
                .newInstance()
                .scheme("https")
                .host(globalExpandParams["cluster"] + "-master.paas.skead.no")
                .port(8443)
                .pathSegment("console/project", project, "browse/pods", pod)
                .queryParam("tab", it)
                .build()
                .toUriString()
            "ocp console" to url
        }.toMap()
    }

    fun createLink(linkString: String, rel: String, expandParams: Map<String, String> = mapOf()): Pair<String, String> {
        val expanded = (globalExpandParams + expandParams).entries
            .fold(linkString) { acc, e -> acc.replace("""{${e.key}}""", e.value) }
        return rel to expanded
    }
}