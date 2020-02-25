package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.fabric8.kubernetes.api.model.KubernetesList
import io.fabric8.kubernetes.internal.KubernetesDeserializer
import mu.KotlinLogging
import no.skatteetaten.aurora.filter.logging.AuroraHeaderFilter
import no.skatteetaten.aurora.filter.logging.RequestKorrelasjon
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.KubernetesRetryConfiguration
import no.skatteetaten.aurora.kubernetes.KubnernetesClientConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.util.StreamUtils
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit

enum class ServiceTypes {
    CANTUS
}

@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class TargetService(val value: ServiceTypes)

private val logger = KotlinLogging.logger {}

@Configuration
@EnableScheduling
class ApplicationConfig(
    @Value("\${spring.application.name}") val applicationName: String,
    val kubeernetesClientConfig: KubnernetesClientConfiguration
) : BeanPostProcessor {

    @Bean
    fun tokenProvider(): TokenFetcher {
        return object : TokenFetcher {
            override fun token(): String {
                return (SecurityContextHolder.getContext().authentication.principal as no.skatteetaten.aurora.mokey.controller.security.User).token
            }
        }
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
        if (beanName == "_halObjectMapper" && bean is ObjectMapper) {
            configureObjectMapper(bean)
        }

        KubernetesDeserializer.registerCustomKind(
            "skatteetaten.no/v1",
            "ApplicationDeploymentList",
            KubernetesList::class.java
        )

        KubernetesDeserializer.registerCustomKind(
            "skatteetaten.no/v1",
            "ApplicationDeployment",
            ApplicationDeployment::class.java
        )

        return super.postProcessAfterInitialization(bean, beanName)
    }

    @Qualifier("managmenetClient")
    @Bean
    fun managementClient(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?
    ): KubernetesReactorClient {
        return kubeernetesClientConfig.copy(retry = KubernetesRetryConfiguration(times = 0))
            .createServiceAccountReactorClient(builder, trustStore, applicationName)
    }

    @Bean
    fun mapperBuilder(): Jackson2ObjectMapperBuilder = Jackson2ObjectMapperBuilder().apply {
        serializationInclusion(JsonInclude.Include.NON_NULL)
        featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        featuresToEnable(SerializationFeature.INDENT_OUTPUT)
    }

    // TODO: Hvorfor har cantus med seg denne tokenen?
    @Bean
    @TargetService(ServiceTypes.CANTUS)
    fun webClientCantus(
        builder: WebClient.Builder,
        @Value("\${integrations.cantus.url}") cantusUrl: String,
        // TODO: Hvorfor lese denne som ressurs og ikke som File som i kubernetesKlienten?
        @Value("\${mokey.openshift.tokenLocation:file:/var/run/secrets/kubernetes.io/serviceaccount/token}") token: Resource
    ): WebClient {
        logger.info("Configuring Cantus WebClient with base Url={}", cantusUrl)
        val b = webClientBuilder()
            .baseUrl(cantusUrl)
            .exchangeStrategies(ExchangeStrategies.builder().codecs {
                it.defaultCodecs().apply {
                    maxInMemorySize(-1) // unlimited
                }
            }.build())

        try {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${token.readContent()}")
        } catch (e: IOException) {
            logger.info("No token file found, will not add Authorization header to WebClient")
        }

        return b.build()
    }

    fun webClientBuilder(ssl: Boolean = false) =
        WebClient
            .builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(AuroraHeaderFilter.KORRELASJONS_ID, RequestKorrelasjon.getId())

    @Throws(NoSuchAlgorithmException::class, KeyManagementException::class)
    private fun createRequestFactory(readTimeout: Long, connectionTimeout: Long): OkHttp3ClientHttpRequestFactory {
        val okHttpClientBuilder = OkHttpClient().newBuilder()
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .connectTimeout(connectionTimeout, TimeUnit.SECONDS)

        return OkHttp3ClientHttpRequestFactory(okHttpClientBuilder.build())
    }
}

fun Resource.readContent() = StreamUtils.copyToString(this.inputStream, StandardCharsets.UTF_8)
