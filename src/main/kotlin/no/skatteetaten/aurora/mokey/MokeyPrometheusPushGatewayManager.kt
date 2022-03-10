package no.skatteetaten.aurora.mokey

import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.xerial.snappy.Snappy
import prometheus.Remote.WriteRequest
import prometheus.Types
import javax.annotation.PostConstruct

@Component
class MokeyPrometheusPushGatewayManager {

    @PostConstruct
    fun init() {
        push()
    }

    fun push() {
        val sample = Types.Sample.newBuilder().setTimestamp(System.currentTimeMillis()).setValue(1.0).build()
        val label = Types.Label.newBuilder().setName("app").setValue("mokey").build()
        val l2 = Types.Label.newBuilder().setName("__name__").setValue("mokey_test_metric").build()
        val timeseries = Types.TimeSeries.newBuilder().addLabels(label).addLabels(l2).addSamples(sample).build()

        val writeRequest = WriteRequest.newBuilder().addAllTimeseries(listOf(timeseries)).build()
        val compressed = Snappy.compress(writeRequest.toByteArray())

        val retrieve = WebClient
            .create("https://cortex.sits.no/api/v1/push")
            .post()
            .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
            .header(HttpHeaders.CONTENT_ENCODING, "snappy")
            .header("X-Prometheus-Remote-Write-Version", "0.1.0")
            .bodyValue(compressed)
            .retrieve()
            .bodyToMono<String>()
            .doOnError {
                println(it)

                if (it is WebClientResponseException) {
                    println(it.responseBodyAsString)
                }
            }
            .block()

        println(retrieve)
    }
}
