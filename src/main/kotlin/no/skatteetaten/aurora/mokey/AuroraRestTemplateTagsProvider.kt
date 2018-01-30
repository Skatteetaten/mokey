package no.skatteetaten.aurora.mokey

import java.util.Arrays

import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpResponse
import org.springframework.stereotype.Component

import io.micrometer.core.instrument.Tag
import io.micrometer.spring.web.client.RestTemplateExchangeTags
import io.micrometer.spring.web.client.RestTemplateExchangeTagsProvider

@Component
class AuroraRestTemplateTagsProvider : RestTemplateExchangeTagsProvider {

    override fun getTags(urlTemplate: String?, request: HttpRequest,
                         response: ClientHttpResponse?): Iterable<Tag> {

        val host=request.uri.host ?: "none"
        //TODO: Her må vi vel kanskje også skille på internt docker registry og ei?
        val clientName=if(host.contains("docker")) {
            "docker"
        } else {
            "management"
        }

        return Arrays.asList(RestTemplateExchangeTags.method(request),
                RestTemplateExchangeTags.status(response),
                Tag.of("clientName", clientName))
    }

}