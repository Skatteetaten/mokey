package no.skatteetaten.aurora.mokey.controller

import io.prometheus.client.CollectorRegistry
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint
import org.springframework.boot.actuate.metrics.MetricsEndpoint
import org.springframework.boot.actuate.metrics.export.prometheus.PrometheusScrapeEndpoint
import org.springframework.boot.actuate.metrics.export.prometheus.TextOutputFormat
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
@WebEndpoint(id = "prometheus-application-info")
class ApplicationInfoEndpoint(collectorRegistry: CollectorRegistry) : PrometheusScrapeEndpoint(collectorRegistry) {
    @ReadOperation(producesFrom = TextOutputFormat::class)
    override fun scrape(
        format: TextOutputFormat?,
        @Nullable includedNames: MutableSet<String>?
    ): WebEndpointResponse<String> =
        super.scrape(format, setOf("application_info"))
}

@Component
@WebEndpoint(id = "prometheus-application-status")
class ApplicationStatusEndpoint(collectorRegistry: CollectorRegistry) : PrometheusScrapeEndpoint(collectorRegistry) {
    @ReadOperation(producesFrom = TextOutputFormat::class)
    override fun scrape(
        format: TextOutputFormat?,
        @Nullable includedNames: MutableSet<String>?
    ): WebEndpointResponse<String> =
        super.scrape(format, setOf("application_status"))
}

@Component
@WebEndpoint(id = "prometheus")
class MokeyMetricsEndpoint(collectorRegistry: CollectorRegistry, private val metricsEndpoint: MetricsEndpoint) :
    PrometheusScrapeEndpoint(collectorRegistry) {
    @ReadOperation(producesFrom = TextOutputFormat::class)
    override fun scrape(
        format: TextOutputFormat?,
        @Nullable includedNames: MutableSet<String>?
    ): WebEndpointResponse<String> {
        val names = metricsEndpoint.listNames().names
            .map { it.replace(".", "_") } - listOf("application_info", "application_status")
        return super.scrape(format, names.toSet())
    }
}
