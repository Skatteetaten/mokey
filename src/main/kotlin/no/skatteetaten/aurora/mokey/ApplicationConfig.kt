package no.skatteetaten.aurora.mokey

import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import okhttp3.OkHttpClient
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.hateoas.config.EnableHypermediaSupport
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

@Configuration
@EnableScheduling
@EnableHypermediaSupport(type = [HAL])
class ApplicationConfig : WebMvcConfigurer {

    @Bean
    fun client(): OpenShiftClient = DefaultOpenShiftClient()

    override fun configureContentNegotiation(configurer: ContentNegotiationConfigurer) {
        configurer
            .ignoreAcceptHeader(true)
            .favorParameter(true)
            .defaultContentType(MediaType.parseMediaType("application/hal+json;charset=UTF-8"))
    }

    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder.requestFactory({ createRequestFactory(2, 2) })
            .additionalInterceptors(ClientHttpRequestInterceptor { request, body, execution ->
                request.headers.accept = mutableListOf(MediaType.APPLICATION_JSON)
                execution.execute(request, body)
            }).build()
    }

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun createRequestFactory(readTimeout: Long, connectionTimeout: Long): OkHttp3ClientHttpRequestFactory {

        val okHttpClientBuilder = OkHttpClient().newBuilder()
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .connectTimeout(connectionTimeout, TimeUnit.SECONDS)

        return OkHttp3ClientHttpRequestFactory(okHttpClientBuilder.build())
    }
}
