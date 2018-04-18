@file:JvmName("ObjectMapperConfigurer")

package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun configureObjectMapper(objectMapper: ObjectMapper): ObjectMapper {
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    objectMapper.configure(SerializationFeature.INDENT_OUTPUT, true)
    objectMapper.registerModules(JavaTimeModule())
    objectMapper.registerKotlinModule()
    return objectMapper
}