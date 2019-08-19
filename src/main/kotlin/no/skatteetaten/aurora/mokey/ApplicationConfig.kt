package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import io.fabric8.openshift.client.OpenShiftConfigBuilder
import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.hateoas.config.EnableHypermediaSupport
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.util.StreamUtils
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.TcpClient
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory

@Configuration
@EnableScheduling
@EnableHypermediaSupport(type = [HAL])
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
    fun client(): OpenShiftClient {
        val httpClient = OkHttpClient().newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .build()

        return DefaultOpenShiftClient(httpClient, OpenShiftConfigBuilder().build())
    }

    @Bean
    fun webClient(
        builder: WebClient.Builder,
        tcpClient: TcpClient,
        @Value("\${mokey.openshift.tokenLocation:file:/var/run/secrets/kubernetes.io/serviceaccount/token}") token: Resource
    ) = builder
        .baseUrl("https://utv-master.paas.skead.no:8443")
        .defaultHeader("Authorization", "Bearer ${StreamUtils.copyToString(token.inputStream, StandardCharsets.UTF_8)}")
        .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient).compress(true))).build()

    @Bean
    fun tcpClient(
        @Value("\${mokey.httpclient.readTimeout:5000}") readTimeout: Long,
        @Value("\${mokey.httpclient.writeTimeout:5000}") writeTimeout: Long,
        @Value("\${mokey.httpclient.connectTimeout:5000}") connectTimeout: Int,
        trustStore: KeyStore?
    ): TcpClient {
        val trustFactory = TrustManagerFactory.getInstance("X509")
        trustFactory.init(trustStore)

        val sslProvider = SslProvider.builder().sslContext(
            SslContextBuilder
                .forClient()
                .trustManager(trustFactory)
                .build()
        ).build()
        return TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
            .secure(sslProvider)
            .doOnConnected { connection ->
                connection
                    .addHandlerLast(ReadTimeoutHandler(readTimeout, TimeUnit.MILLISECONDS))
                    .addHandlerLast(WriteTimeoutHandler(writeTimeout, TimeUnit.MILLISECONDS))
            }
    }

    @Bean
    fun restTemplate(
        builder: RestTemplateBuilder,
        @Value("\${spring.application.name}") applicationName: String
    ): RestTemplate {
        return builder.requestFactory { createRequestFactory(2, 2) }
            .additionalInterceptors(ClientHttpRequestInterceptor { request, body, execution ->
                request.headers.apply {
                    accept = mutableListOf(MediaType.APPLICATION_JSON)
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

    @ConditionalOnMissingBean(KeyStore::class)
    @Bean
    fun localKeyStore(): KeyStore? = null

    @Profile("openshift")
    @Primary
    @Bean
    fun openshiftSSLContext(@Value("\${trust.store}") trustStoreLocation: String): KeyStore? =
        KeyStore.getInstance(KeyStore.getDefaultType())?.let { ks ->
            ks.load(FileInputStream(trustStoreLocation), "changeit".toCharArray())
            val fis = FileInputStream("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
            CertificateFactory.getInstance("X509").generateCertificates(fis).forEach {
                ks.setCertificateEntry((it as X509Certificate).subjectX500Principal.name, it)
            }
            ks
        }
}
