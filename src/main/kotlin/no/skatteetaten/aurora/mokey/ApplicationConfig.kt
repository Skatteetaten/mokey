package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import io.fabric8.kubernetes.api.model.KubernetesList
import io.fabric8.kubernetes.internal.KubernetesDeserializer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.KubernetesConfiguration
import no.skatteetaten.aurora.kubernetes.KubernetesReactorClient
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.TokenFetcher
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.springboot.getToken
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.core.context.ReactiveSecurityContextHolder
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
class ApplicationConfig(val kubernetesClientConfig: KubernetesConfiguration) : BeanPostProcessor {

    @Bean
    fun tokenFetcher(): TokenFetcher = object : TokenFetcher {
        override suspend fun coToken(audience: String?): String {
            return ReactiveSecurityContextHolder.getContext().awaitFirst().authentication.getToken()
        }
    }

    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    @Qualifier("managementClient")
    @Bean
    fun managementClient(
        builder: WebClient.Builder,
        @Qualifier("kubernetesClientWebClient") trustStore: KeyStore?,
    ): KubernetesReactorClient =
        kubernetesClientConfig.copy(
            retry = RetryConfiguration(0),
            timeout = kubernetesClientConfig.timeout.copy(read = Duration.ofSeconds(2))
        ).createServiceAccountReactorClient(builder, trustStore).build()

    // Management interface parsing needs this
    @Bean
    fun mapperBuilder(): Jackson2ObjectMapperBuilder = Jackson2ObjectMapperBuilder().apply {
        serializationInclusion(NON_NULL)
        featuresToDisable(WRITE_DATES_AS_TIMESTAMPS)
        featuresToEnable(INDENT_OUTPUT)
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
        @Value("\${integrations.cantus.url}") cantusUrl: String,
    ): WebClient {
        logger.info("Configuring Cantus WebClient with base Url={}", cantusUrl)

        return builder
            .baseUrl(cantusUrl)
            .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .exchangeStrategies(
                ExchangeStrategies.builder().codecs {
                    it.defaultCodecs().apply {
                        maxInMemorySize(-1) // unlimited
                    }
                }.build()
            ).build()
    }
}

suspend fun <A, B> Iterable<A>.pmapIO(f: suspend (A) -> B): List<B> = coroutineScope {
    map { async(MDCContext()) { f(it) } }.awaitAll()
}
