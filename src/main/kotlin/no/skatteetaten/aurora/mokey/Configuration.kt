package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.client.utils.HttpClientUtils
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import io.fabric8.openshift.client.OpenShiftConfigBuilder
import okhttp3.Dns
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.net.InetAddress
import java.net.UnknownHostException
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

        val config = OpenShiftConfigBuilder()
                .withMasterUrl("https://utv-master.paas.skead.no:8443").build()

        val httpClient = HttpClientUtils.createHttpClient(config).newBuilder()
                .dns { resolveDns(it) }
                .build()
        return DefaultOpenShiftClient(httpClient, config)
    }

    private fun resolveDns(hostname: String): MutableList<InetAddress>? {
        val range = (0..3)
        range.forEach {
            try {
                return Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                if (it == range.last) {
                    throw e
                }
                logger.info("Retry dns lookup times={}", it)
                Thread.sleep(500)
            }
        }
        throw UnknownHostException(hostname)
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