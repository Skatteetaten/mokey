package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit


@Configuration
class Configuration {

    val logger: Logger = LoggerFactory.getLogger(no.skatteetaten.aurora.mokey.Configuration::class.java)

    @Bean
    @Primary
    fun mapper(): ObjectMapper {
        return jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }


    @Bean
    fun client(): OpenShiftClient {
        logger.debug("Create OpenShift client")
        val range = (0..3)
        range.forEach {
            try {
                val client = DefaultOpenShiftClient()
                val supports = client.supportsApiPath("/api/v1")
                logger.debug("We support v1 api={}", supports)
                return client
            } catch (e: Exception) {
                if (it == range.last) {
                    throw e
                }
                logger.debug("Can not resolve DNS retry")
            }
        }
        throw Exception("Can not create OpenShift client")
    }

    @Bean
    fun restTemplate(): RestTemplate {
        logger.debug("Create RestTemplate")
        //CONFIG
        return RestTemplate(createRequestFactory(1, 1))
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun createRequestFactory(readTimeout: Long, connectionTimeout: Long): OkHttp3ClientHttpRequestFactory {

        val okHttpClientBuilder = OkHttpClient().newBuilder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)


        return OkHttp3ClientHttpRequestFactory(okHttpClientBuilder.build())
    }

}