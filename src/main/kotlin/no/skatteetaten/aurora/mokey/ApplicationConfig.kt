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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.hateoas.config.EnableHypermediaSupport
import org.springframework.hateoas.config.EnableHypermediaSupport.HypermediaType.HAL
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.TcpClient
import java.security.KeyManagementException
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
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
            .addInterceptor(LoggingInterceptor())
            .build()

        return DefaultOpenShiftClient(httpClient, OpenShiftConfigBuilder().build())
    }

    @Bean
    fun webClient(builder: WebClient.Builder, tcpClient: TcpClient) = builder
        .baseUrl("https://utv-master.paas.skead.no:8443")
        .defaultHeader("Authorization", "Bearer")
        .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient).compress(true))).build()

    @Bean
    fun tcpClient(
        @Value("\${cantus.httpclient.readTimeout:5000}") readTimeout: Long,
        @Value("\${cantus.httpclient.writeTimeout:5000}") writeTimeout: Long,
        @Value("\${cantus.httpclient.connectTimeout:5000}") connectTimeout: Int,
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
}

class LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        println(request.url)
        return chain.proceed(request)
    }
}
