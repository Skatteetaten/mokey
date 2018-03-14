package no.skatteetaten.aurora.mokey

import io.micrometer.core.instrument.Tag
import org.springframework.boot.actuate.metrics.web.client.RestTemplateExchangeTags
import org.springframework.boot.actuate.metrics.web.client.RestTemplateExchangeTagsProvider
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component
import java.util.Arrays

@Component
class AuroraRestTemplateTagsProvider : RestTemplateExchangeTagsProvider {

    override fun getTags(urlTemplate: String?, request: HttpRequest,
                         response: ClientHttpResponse?): Iterable<Tag> {

        val host = request.uri.host ?: "none"
        val clientName = when {
            host.startsWith("172") -> "docker-internal"
            host.startsWith("10") -> "management"
            else -> "docker"
        }

        return Arrays.asList(RestTemplateExchangeTags.method(request),
                RestTemplateExchangeTags.status(response),
                Tag.of("clientName", clientName))
    }

}