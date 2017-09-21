package no.skatteetaten.aurora.mokey.controller

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Gauge
import no.skatteetaten.aurora.mokey.facade.AuroraApplicationFacade
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.Response
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/aurora/application")
class AuroraApplicationController(val facade: AuroraApplicationFacade, val registry: CollectorRegistry) {

    @GetMapping("/namespace/{namespace}/application/{application}")
    fun get(@PathVariable namespace: String, @PathVariable application: String): Response {
        val result = facade.findApplication(namespace, application)

        val lst: List<AuroraApplication> = result?.let { listOf(it) } ?: emptyList()

        return Response(items = lst)


    }

    @GetMapping("/namespace/{namespace}/application/{application}/health")
    fun health(@PathVariable namespace: String, @PathVariable application: String): Response {

        val gauge = Gauge.build()
                .name("aurora_status")
                .help("Aurora Health of application")
                .labelNames("name", "namespace", "aurora_version", "rule")
                .register(registry) //do not register with vanilla register here. I do this for testing for now

        val result = facade.findApplication(namespace, application)


        result?.let {
            facade.calculateHealth(it, gauge)
        }
        val lst: List<AuroraApplication> = result?.let { listOf(it) } ?: emptyList()

        //  val pg = PushGateway("127.0.0.1:9091")
        //  pg.pushAdd(registry, "aurora_status_generator")

        return Response(items = lst)


    }

}


