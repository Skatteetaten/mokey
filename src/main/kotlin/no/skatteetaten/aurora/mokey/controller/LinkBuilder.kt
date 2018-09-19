package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.hateoas.Link
import org.springframework.web.util.UriComponentsBuilder

@Configuration
class LinkBuilderFactory(
    @Value("\${boober-api-url}") val booberApiUrl: String,
    @Value("\${metrics-hostname}") val metricsHostname: String,
    @Value("\${splunk-hostname}") val splunkHostname: String,
    @Value("\${openshift-cluster}") val cluster: String
) {
    @Bean
    fun linkBuilder(): LinkBuilder = LinkBuilder(booberApiUrl, expandParams)
}

class LinkBuilder(private val booberApiUrl: String, private val globalExpandParams: Map<String, String>) {

    fun applyResult(auroraConfigName: String, deployId: String): Link {
        return createLink(
            UriComponentsBuilder
                .fromHttpUrl(booberApiUrl)
                .path("/v1/apply-result/$auroraConfigName/$deployId")
                .build().toUriString(), "ApplyResult"
        )
    }

    fun files(deploymentCommand: ApplicationDeploymentCommand) : List<Link>{

        val uriComponents = UriComponentsBuilder.fromHttpUrl(booberApiUrl)
            .pathSegment( "v1", "auroraconfig", deploymentCommand.auroraConfig.name)
            .queryParam("environment", deploymentCommand.applicationDeploymentRef.environment)
            .queryParam("application", deploymentCommand.applicationDeploymentRef.application)

        val currentLink = uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.refName).build()
                .encode()
                .toUriString()
        val deployedLink =
            uriComponents.cloneBuilder().queryParam("reference", deploymentCommand.auroraConfig.resolvedRef).build()
                .encode()
                .toUriString()

        return listOf(
            createLink(currentLink, "FilesCurrent"),
            createLink(deployedLink, "FilesDeployed")
        )
    }

    fun deploymentSpec(deploymentCommand: ApplicationDeploymentCommand): List<Link> {
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

        return listOf(
            createLink(currentLink, "DeploymentSpecCurrent"),
            createLink(deployedLink, "DeploymentSpecDeployed")
        )
    }

    fun createLink(linkString: String, rel: String, expandParams: Map<String, String> = mapOf()): Link {
        val expanded = (globalExpandParams + expandParams).entries
            .fold(linkString) { acc, e -> acc.replace("""{${e.key}}""", e.value) }
        return Link(expanded, rel)
    }
}