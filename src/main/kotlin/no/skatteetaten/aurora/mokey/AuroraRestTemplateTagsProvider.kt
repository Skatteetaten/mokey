package no.skatteetaten.aurora.mokey

import io.micrometer.core.instrument.Tag
import java.util.Arrays
import org.springframework.boot.actuate.metrics.web.client.RestTemplateExchangeTags
import org.springframework.boot.actuate.metrics.web.client.RestTemplateExchangeTagsProvider
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component

@Component
class AuroraRestTemplateTagsProvider : RestTemplateExchangeTagsProvider {

    override fun getTags(
        urlTemplate: String?,
        request: HttpRequest,
        response: ClientHttpResponse?
    ): Iterable<Tag> {

        return Arrays.asList(
            RestTemplateExchangeTags.method(request),
            RestTemplateExchangeTags.status(response),
            RestTemplateExchangeTags.uri(urlTemplate)
        )
    }
}
