package no.skatteetaten.aurora.mokey.controller

import uk.q3c.rest.hal.HalLink
import uk.q3c.rest.hal.HalResource
import uk.q3c.rest.hal.Links

data class Link(val rel: String, val href: String) {
    val halLink: HalLink
        get() = HalLink(href)
}

fun HalResource.link(link: Link) = this.link(link.rel, link.halLink)

fun List<Link>.toLinks() = Links().also {
    forEach { link ->
        it.add(link.rel, link.halLink)
    }
}
