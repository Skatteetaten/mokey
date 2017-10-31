package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary


@Configuration
class Configuration {

    @Bean
    @Primary
    fun mapper(): ObjectMapper {
        return jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }


    @Bean
    fun client(): OpenShiftClient {
        return DefaultOpenShiftClient()
    }
}