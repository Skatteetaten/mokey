package no.skatteetaten.aurora.mokey.controller

import uk.q3c.rest.hal.HalLink
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

data class Link(val rel: String, val href: String, val baseUrl: String = "") {

    val halLink: HalLink
        get() = HalLink("$baseUrl/$href")
}

fun HalResource.link(link: Link) = this.link(link.rel, HalLink(link.href))

fun List<Link>.toLinks() = Links().also {
    this.forEach { link ->
        it.add(link.rel, link.halLink)
    }
}
