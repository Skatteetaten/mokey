package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.CachedApplicationDataService
import okhttp3.OkHttpClient
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

@Configuration
class ApplicationConfig {
    @Bean
    @Primary
    fun mapper(): ObjectMapper {
        return jacksonObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
    }

    @Bean
    fun client(): OpenShiftClient {
        return DefaultOpenShiftClient()
    }


    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder.requestFactory({ createRequestFactory(2, 2) }).build()
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun createRequestFactory(readTimeout: Long, connectionTimeout: Long): OkHttp3ClientHttpRequestFactory {

        val okHttpClientBuilder = OkHttpClient().newBuilder()
                .readTimeout(readTimeout, TimeUnit.SECONDS)
                .connectTimeout(connectionTimeout, TimeUnit.SECONDS)


        return OkHttp3ClientHttpRequestFactory(okHttpClientBuilder.build())
    }
}

@SpringBootApplication
@EnableScheduling
class Main(val applicationDataService: CachedApplicationDataService) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val environments = listOf("aurora", "paas", "sirius-utv1").map { Environment.fromNamespace(it) }
        applicationDataService.refreshCache(environments)
    }

}


fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}
