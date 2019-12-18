package no.skatteetaten.aurora.openshift.webclient

import io.netty.channel.ChannelOption
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.timeout.ReadTimeoutHandler
import io.netty.handler.timeout.WriteTimeoutHandler
import java.io.FileInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory
import mu.KotlinLogging
import no.skatteetaten.aurora.filter.logging.AuroraHeaderFilter
import no.skatteetaten.aurora.filter.logging.RequestKorrelasjon
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.util.StreamUtils
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import reactor.netty.tcp.SslProvider
import reactor.netty.tcp.TcpClient

private val logger = KotlinLogging.logger {}

@Configuration
class OpenShiftClientConfig(@Value("\${spring.application.name}") val applicationName: String) {

    @Bean
    fun openShiftClient(webClient: WebClient) = OpenShiftClient(webClient)

    @Bean
    fun webClient(
        builder: WebClient.Builder,
        tcpClient: TcpClient,
        @Value("\${openshift.url}") openshiftUrl: String,
        @Value("\${mokey.openshift.tokenLocation:file:/var/run/secrets/kubernetes.io/serviceaccount/token}") token: Resource
    ): WebClient {
        val b = builder
            .baseUrl(openshiftUrl)
            .defaultHeaders(applicationName)
            .clientConnector(ReactorClientHttpConnector(HttpClient.from(tcpClient).compress(true)))

        try {
            b.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${token.readContent()}")
        } catch (e: IOException) {
            logger.info("No token file found, will not add Authorization header to WebClient")
        }

        return b.build()
    }

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

private fun WebClient.Builder.defaultHeaders(applicationName: String) = this
    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
    .defaultHeader("KlientID", applicationName)
    .defaultHeader(AuroraHeaderFilter.KORRELASJONS_ID, RequestKorrelasjon.getId())

fun Resource.readContent() = StreamUtils.copyToString(this.inputStream, StandardCharsets.UTF_8)
