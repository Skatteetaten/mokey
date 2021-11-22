package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.web.util.UriComponentsBuilder.fromHttpUrl
import java.nio.file.Files.createLink

@Configuration
class LinkBuilderFactory(
    @Value("\${integrations.boober.url}") val booberApiUrl: String,
    @Value("\${integrations.metrics.url}") val metricsHostname: String,
    @Value("\${integrations.splunk.url}") val splunkHostname: String,
    @Value("\${openshift.cluster}") val cluster: String,
    @Value("\${openshift.majorVersion:3}") val openshiftVersion: Int
) {
    @Bean
    fun linkBuilder(): LinkBuilder = LinkBuilder(booberApiUrl, expandParams, openshiftVersion)
}

class LinkBuilder(
    private val booberApiUrl: String,
    private val globalExpandParams: Map<String, String>,
    private val openShiftVersion: Int = 3
) {
    fun applyResult(
        auroraConfigName: String,
        deployId: String,
    ) = createLink(
        fromHttpUrl(booberApiUrl)
            .path("/v1/apply-result/$auroraConfigName/$deployId")
            .build().toUriString(),
        "ApplyResult"
    )

    fun apply(deploymentCommand: ApplicationDeploymentCommand): Link {
        val uriComponents = fromHttpUrl(booberApiUrl)
            .pathSegment("v1", "apply", deploymentCommand.auroraConfig.name)
            .queryParam("reference", deploymentCommand.auroraConfig.refName).build()
            .encode()
            .toUriString()

        return createLink(uriComponents, "Apply")
    }

    fun auroraConfigFile(deploymentCommand: ApplicationDeploymentCommand): List<Link> {
        val uriComponents = fromHttpUrl(booberApiUrl)
            .pathSegment(
                "v1",
                "auroraconfig",
                deploymentCommand.auroraConfig.name,
                "{fileName}"
            )
        val currentLink =
            uriComponents.cloneBuilder().queryParam(
                "reference",
                deploymentCommand.auroraConfig.refName
            ).build()
                .encode()
                .toUriString()
        val deployedLink =
            uriComponents.cloneBuilder().queryParam(
                "reference",
                deploymentCommand.auroraConfig.resolvedRef
            ).build()
                .encode()
                .toUriString()

        return listOf(
            createLink(currentLink, "AuroraConfigFileCurrent"),
            createLink(deployedLink, "AuroraConfigFileDeployed")
        )
    }

    fun files(deploymentCommand: ApplicationDeploymentCommand): List<Link> {
        val uriComponents = fromHttpUrl(booberApiUrl)
            .pathSegment("v1", "auroraconfig", deploymentCommand.auroraConfig.name)
            .queryParam("environment", deploymentCommand.applicationDeploymentRef.environment)
            .queryParam("application", deploymentCommand.applicationDeploymentRef.application)
        val currentLink = uriComponents.cloneBuilder().queryParam(
            "reference",
            deploymentCommand.auroraConfig.refName
        ).build()
            .encode()
            .toUriString()
        val deployedLink = uriComponents.cloneBuilder().queryParam(
            "reference",
            deploymentCommand.auroraConfig.resolvedRef
        ).build()
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
        val uriComponents = fromHttpUrl(booberApiUrl).pathSegment(
            "v1",
            "auroradeployspec",
            deploymentCommand.auroraConfig.name,
            deploymentCommand.applicationDeploymentRef.environment,
            deploymentCommand.applicationDeploymentRef.application
        )

        overridesQueryParam?.let {
            uriComponents.queryParam("overrides", it)
        }

        val currentLink = uriComponents.cloneBuilder().queryParam(
            "reference",
            deploymentCommand.auroraConfig.refName
        ).build()
            .encode()
            .toUriString()
        val deployedLink = uriComponents.cloneBuilder().queryParam(
            "reference",
            deploymentCommand.auroraConfig.resolvedRef
        ).build()
            .encode()
            .toUriString()

        return listOf(
            createLink(currentLink, "DeploymentSpecCurrent"),
            createLink(deployedLink, "DeploymentSpecDeployed")
        )
    }

    fun openShiftConsoleLinks(pod: String, project: String): List<Link> = listOf(
        "details",
        "environment",
        "terminal",
        "events",
        "logs",
    ).map {
        Link(
            "ocp_console_$it",
            when (openShiftVersion) {
                3 -> {
                    UriComponentsBuilder
                        .newInstance()
                        .scheme("https")
                        .host(clusterHost())
                        .port(8443)
                        .pathSegment("console/project", project, "browse/pods", pod)
                        .queryParam("tab", it)
                        .build()
                        .toUriString()
                }
                else -> {
                    val tabSegment = when (it) {
                        "details" -> ""
                        else -> it
                    }
                    UriComponentsBuilder
                        .newInstance()
                        .scheme("https")
                        .host("console-openshift-console.apps.${globalExpandParams["cluster"]}.paas.skead.no")
                        .pathSegment("k8s/ns", project, "pods", pod, tabSegment)
                        .build()
                        .toUriString()
                }
            }
        )
    }

    private fun clusterHost(): String = when (val cluster = globalExpandParams["cluster"]) {
        "utv04" -> "$cluster.paas.skead.no"
        else -> "$cluster-master.paas.skead.no"
    }

    fun createMokeyLink(
        rel: String,
        href: String,
    ): Link =
        Link(rel, "${fromHttpUrl("http://mokey").toUriString()}$href")

    fun createLink(
        linkString: String,
        rel: String,
        expandParams: Map<String, String> = mapOf(),
    ): Link {
        val expanded = (globalExpandParams + expandParams).entries
            .fold(linkString) { acc, e -> acc.replace("""{${e.key}}""", e.value) }

        return Link(rel, expanded)
    }
}
