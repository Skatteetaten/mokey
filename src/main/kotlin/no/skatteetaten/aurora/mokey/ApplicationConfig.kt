package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import no.skatteetaten.aurora.openshift.webclient.OpenShiftClientConfig
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.hateoas.config.EnableHypermediaSupport
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL
import org.springframework.http.HttpHeaders
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

@Configuration
@EnableScheduling
@EnableHypermediaSupport(type = [HAL])
@Import(OpenShiftClientConfig::class)
class ApplicationConfig : BeanPostProcessor {

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        if (beanName == "_halObjectMapper" && bean is ObjectMapper) {
            configureObjectMapper(bean)
        }

        return super.postProcessAfterInitialization(bean, beanName)
    }

    @Bean
    fun mapperBuilder(): Jackson2ObjectMapperBuilder = Jackson2ObjectMapperBuilder().apply {
        serializationInclusion(JsonInclude.Include.NON_NULL)
        featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        featuresToEnable(SerializationFeature.INDENT_OUTPUT)
    }

    @Bean
    fun restTemplate(
        builder: RestTemplateBuilder,
        @Value("\${spring.application.name}") applicationName: String
    ): RestTemplate {
        return builder.requestFactory { createRequestFactory(2, 2) }
            .additionalInterceptors(ClientHttpRequestInterceptor { request, body, execution ->
                request.headers.apply {
                    set(HttpHeaders.ACCEPT, "application/vnd.spring-boot.actuator.v2+json")
                    set("KlientID", applicationName)
                }

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
