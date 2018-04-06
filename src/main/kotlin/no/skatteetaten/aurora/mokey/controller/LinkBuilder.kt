package no.skatteetaten.aurora.mokey.controller

import org.springframework.beans.factory.annotation.Value
import org.springframework.hateoas.Link
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

@Component
class LinkBuilder(@Value("\${boober-api-url}") val booberApiUrl: String) {

    fun applyResult(auroraConfigName: String?, deployId: String?): Link? =
            Link(UriComponentsBuilder
                    .fromHttpUrl(booberApiUrl)
                    .path("/v1/apply-result/${auroraConfigName}/${deployId}")
                    .build().toUriString(),
                    "ApplyResult"
            )
}