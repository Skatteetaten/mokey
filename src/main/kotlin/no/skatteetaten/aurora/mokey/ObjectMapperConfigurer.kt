@file:JvmName("ObjectMapperConfigurer")

package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.hateoas.hal.Jackson2HalModule

fun createObjectMapper() =
    configureObjectMapper(ObjectMapper())

fun configureObjectMapper(objectMapper: ObjectMapper): ObjectMapper {
    return objectMapper.apply {
        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        configure(SerializationFeature.INDENT_OUTPUT, true)
        registerModules(JavaTimeModule(), Jackson2HalModule())
        registerKotlinModule()
        enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
        disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }
}