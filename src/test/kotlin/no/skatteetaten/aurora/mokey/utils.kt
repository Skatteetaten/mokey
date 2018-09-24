package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

fun prettyPrint(s: String): String? = jacksonObjectMapper().apply {
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    registerModules(JavaTimeModule())
}.let {
    it.writerWithDefaultPrettyPrinter().writeValueAsString(it.readValue(s))
}
