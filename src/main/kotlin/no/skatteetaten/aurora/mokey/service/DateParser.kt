package no.skatteetaten.aurora.mokey.service

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object DateParser {
    val formatters = listOf(
        DateTimeFormatter.ISO_DATE_TIME, // Ex: 2018-03-23T10:53:31Z
        // Locale must be set to Locale.UK to parse correctly on the server.
        DateTimeFormatter.ofPattern("dd.MM.yyyy '@' HH:mm:ss z", Locale.UK), // Ex: 26.03.2018 @ 13:31:39 CEST
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z", Locale.UK) // Ex: 2019-03-28 13:26:58 +0100
    )

    fun parseString(dateString: String): Instant? {
        formatters.forEach {
            try {
                return it.parse(dateString, Instant::from)
            } catch (e: DateTimeParseException) {
            }
        }
        return null
    }
}