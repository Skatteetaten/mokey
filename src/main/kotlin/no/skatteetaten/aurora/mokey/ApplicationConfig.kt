package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import io.fabric8.kubernetes.api.model.KubernetesList
import io.fabric8.kubernetes.internal.KubernetesDeserializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import no.skatteetaten.aurora.filter.logging.AuroraHeaderFilter
import no.skatteetaten.aurora.filter.logging.RequestKorrelasjon
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.KubnernetesClientConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.kubernetes.defaultHeaders
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import java.security.KeyStore
import java.time.Duration

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

    @Qualifier("managementClient")
    @Bean
    fun managementClient(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?
    ): KubernetesReactorClient {
        return kubeernetesClientConfig.copy(
            timeout = kubeernetesClientConfig.timeout.copy(read = Duration.ofSeconds(2))
        ).createServiceAccountReactorClient(builder, trustStore).apply {
            webClientBuilder.defaultHeaders(applicationName)
        }.build()
    }

    // Management interface parsing needs this
    @Bean
    fun mapperBuilder(): Jackson2ObjectMapperBuilder = Jackson2ObjectMapperBuilder().apply {
        serializationInclusion(JsonInclude.Include.NON_NULL)
        featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        featuresToEnable(SerializationFeature.INDENT_OUTPUT)
    }

    @Bean
    fun tokenProvider(): TokenFetcher {
        return object : TokenFetcher {
            override fun token(): String {
                return (SecurityContextHolder.getContext().authentication.principal as no.skatteetaten.aurora.mokey.controller.security.User).token
            }
        }
    }

    override fun postProcessAfterInitialization(bean: Any, beanName: String): Any? {
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

    @Bean
    @TargetService(ServiceTypes.CANTUS)
    fun webClientCantus(
        builder: WebClient.Builder,
        @Value("\${integrations.cantus.url}") cantusUrl: String
    ): WebClient {
        logger.info("Configuring Cantus WebClient with base Url={}", cantusUrl)
        val b = builder
            .baseUrl(cantusUrl)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(AuroraHeaderFilter.KORRELASJONS_ID, RequestKorrelasjon.getId())
            .defaultHeader("User-Agent", applicationName)
            .exchangeStrategies(ExchangeStrategies.builder().codecs {
                it.defaultCodecs().apply {
                    maxInMemorySize(-1) // unlimited
                }
            }.build())

        return b.build()
    }
}

suspend fun <A, B> Iterable<A>.pmapIO(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async(Dispatchers.IO + MDCContext()) { f(it) } }.awaitAll()
}
