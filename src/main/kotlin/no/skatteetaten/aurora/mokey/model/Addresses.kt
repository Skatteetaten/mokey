package no.skatteetaten.aurora.mokey.model

import java.net.URI
import java.time.Instant

interface Address {
    val url: URI
    val time: Instant?
    val available: Boolean
    val status: String?
}

data class ServiceAddress(
    override val url: URI,
    override val time: Instant?,
    override val available: Boolean = true,
    override val status: String? = null
) : Address

data class RouteAddress(
    override val url: URI,
    override val time: Instant?,
    override val available: Boolean = false,
    override val status: String?
) : Address

data class WebSealAddress(
    override val url: URI,
    override val time: Instant?,
    override val available: Boolean = false,
    override val status: String?,
    val roles: List<String>
) : Address

/*
wembley.sits.no/serviceName	Navnet på service. Dette er det logiske navnet som det struktureres under på BigIP	Ja
wembley.sits.no/apiPaths
Komma-sepearert liste over hvilke pather som skal være med. F.eks.
"wembley.sits.no|apiPaths":"/web/test/,/api/test/"
Ja
wembley.sits.no/externalHost	F.eks. skatt-utv1.sits.no. Det er begrensninger på hva som kan brukes hvor.	Ja
wembley.sits.no/asmPolicy
 */
data class BigIPAddress(
    override val url: URI,
    override val time: Instant?,
    override val available: Boolean = false,
    override val status: String?
) : Address
