package no.skatteetaten.aurora.mokey.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.hateoas.Link
import org.springframework.web.util.UriComponentsBuilder

@Configuration
class LinkBuilderFactory(
        @Value("\${boober-api-url}") val booberApiUrl: String,
        @Value("\${metrics-hostname}") val metricsHostname: String,
        @Value("\${openshift-cluster}") val cluster: String
) {
    @Bean
    fun linkBuilder(): LinkBuilder {
        val globalExpandParams = mapOf(
                "metricsHostname" to metricsHostname,
                "cluster" to cluster
        )
        return LinkBuilder(booberApiUrl, globalExpandParams)
    }
}


class LinkBuilder(private val booberApiUrl: String, private val globalExpandParams: Map<String, String>) {

    fun applyResult(auroraConfigName: String, deployId: String): Link {
        return createLink(UriComponentsBuilder
                .fromHttpUrl(booberApiUrl)
                .path("/v1/apply-result/${auroraConfigName}/${deployId}")
                .build().toUriString(), "ApplyResult")
    }

    fun createLink(linkString: String, rel: String, expandParams: Map<String, String> = mapOf()): Link {
        val expanded = (globalExpandParams + expandParams).entries
                .fold(linkString) { acc, e -> acc.replace("""{${e.key}}""", e.value) }
        return Link(expanded, rel)
    }
}