package no.skatteetaten.aurora.mokey.service

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object DateParser {
    val formatters = listOf(
            DateTimeFormatter.ISO_DATE_TIME, // Ex: 2018-03-23T10:53:31Z
            DateTimeFormatter.ofPattern("dd.MM.yyyy '@' HH:mm:ss z") // Ex: 26.03.2018 @ 13:31:39 CEST
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