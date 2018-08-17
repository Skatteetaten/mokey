package no.skatteetaten.aurora.mokey.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.ApplicationCommand
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

    fun deploymentSepc(command: ApplicationCommand): Link {
        val overridesQueryParam = command.overrideFiles.takeIf { it.isNotEmpty() }?.let {
            jacksonObjectMapper().writeValueAsString(it)
        }

        val uriComponents = UriComponentsBuilder.fromHttpUrl(booberApiUrl)
            .pathSegment(
                "api",
                "v1",
                "auroradeployspec",
                command.auroraConfig.name,
                command.applicationId.environment,
                command.applicationId.application
            )
            .queryParam("reference", command.auroraConfig.refName)

        overridesQueryParam?.let {
            uriComponents.queryParam("overrides", it)
        }
        return createLink(uriComponents.build().toUriString(), "DeploymentSpec")
    }

    fun createLink(linkString: String, rel: String, expandParams: Map<String, String> = mapOf()): Link {
        val expanded = (globalExpandParams + expandParams).entries
            .fold(linkString) { acc, e -> acc.replace("""{${e.key}}""", e.value) }
        return Link(expanded, rel)
    }
}