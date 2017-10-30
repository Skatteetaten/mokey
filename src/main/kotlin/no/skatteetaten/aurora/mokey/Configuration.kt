package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.ssl.SSLContexts
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.security.cert.X509Certificate

@Configuration
class Configuration {

    @Bean
    @Primary
    fun mapper(): ObjectMapper {
        return jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }


    @Bean
    @Primary
    fun restTemplate(): RestTemplate {

        fun createSslTrustAllHttpClient(): CloseableHttpClient? {
            val acceptingTrustStrategy = { chain: Array<X509Certificate>, authType: String -> true }

            val sslContext = SSLContexts.custom()
                    .loadTrustMaterial(null, acceptingTrustStrategy)
                    .build()

            val csf = SSLConnectionSocketFactory(sslContext)

            val httpClient = HttpClients.custom()
                    .setSSLSocketFactory(csf)
                    .build()
            return httpClient
        }

        val clientHttpRequestFactory = HttpComponentsClientHttpRequestFactory().apply {
            setReadTimeout(2000)
            setConnectTimeout(2000)
            httpClient = createSslTrustAllHttpClient()
        }

        return RestTemplate(clientHttpRequestFactory)
    }
}