package no.skatteetaten.aurora.mokey.service

import java.time.Instant
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import java.time.format.DateTimeFormatter.ofPattern
import java.util.Locale.UK

object DateParser {
    private val formatters = listOf(
        ISO_DATE_TIME, // Ex: 2018-03-23T10:53:31Z
        // Must add Locale.UK to pattern to parse correctly on the server.
        ofPattern("dd.MM.yyyy '@' HH:mm:ss z", UK), // Ex: 26.03.2018 @ 13:31:39 CEST
        ofPattern("yyyy-MM-dd HH:mm:ss Z", UK) // Ex: 2019-03-28 13:26:58 +0100
    )

    fun parseString(dateString: String): Instant? = formatters.mapNotNull {
        runCatching {
            it.parse(dateString, Instant::from)
        }.getOrNull()
    }.firstOrNull()
}
