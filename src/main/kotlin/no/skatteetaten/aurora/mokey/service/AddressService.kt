package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.Service
import no.skatteetaten.aurora.mokey.extensions.addIfNotNull
import no.skatteetaten.aurora.mokey.extensions.created
import no.skatteetaten.aurora.mokey.extensions.ensureStartWith
import no.skatteetaten.aurora.mokey.extensions.marjoryDone
import no.skatteetaten.aurora.mokey.extensions.marjoryOpen
import no.skatteetaten.aurora.mokey.extensions.marjoryRoles
import no.skatteetaten.aurora.mokey.extensions.success
import no.skatteetaten.aurora.mokey.extensions.wembleyHost
import no.skatteetaten.aurora.mokey.extensions.wembleyService
import no.skatteetaten.aurora.mokey.model.Address
import no.skatteetaten.aurora.mokey.model.BigIPAddress
import no.skatteetaten.aurora.mokey.model.RouteAddress
import no.skatteetaten.aurora.mokey.model.ServiceAddress
import no.skatteetaten.aurora.mokey.model.WebSealAddress
import java.net.URI.create

@org.springframework.stereotype.Service
class AddressService(val openshiftClient: OpenShiftServiceAccountClient) {
    suspend fun getIngressAddresses(
        namespace: String,
        name: String,
    ): List<Address> {
        val metadata = newObjectMeta {
            this.namespace = namespace
            this.labels = mapOf("app" to name)
        }
        val services = openshiftClient.getServices(metadata)
        val ingress = openshiftClient.getIngresses(metadata)
        val serviceAddresses = services.map { ServiceAddress(create("http://${it.metadata.name}"), it.created) }
        val routeAddresses = ingress.flatMap {
            val rule = it.spec.rules.first()
            val host = rule.host
            val path = rule.http.paths.first().path?.ensureStartWith("/") ?: ""
            val success = true
            val protocol = "http"
            val route = RouteAddress(create("$protocol://${host}$path"), it.created, success, "OK")

            listOf(route)
        }

        return serviceAddresses.addIfNotNull(routeAddresses)
    }

    suspend fun getAddresses(namespace: String, name: String): List<Address> {
        val metadata = newObjectMeta {
            this.namespace = namespace
            this.labels = mapOf("app" to name)
        }
        val services = openshiftClient.getServices(metadata)
        val routes = openshiftClient.getRoutes(metadata)
        val serviceAddresses = services.map { ServiceAddress(create("http://${it.metadata.name}"), it.created) }
        val routeAddresses = routes.flatMap {
            val path = it.spec.path?.ensureStartWith("/") ?: ""
            val status = it.status.ingress.first().conditions.first()
            val success = status.type == "Admitted" && status.status == "True"
            val protocol = when {
                it.spec.tls != null -> "https"
                else -> "http"
            }
            val route = RouteAddress(create("$protocol://${it.spec.host}$path"), it.created, success, status.reason)
            val bigIpRoute = it.wembleyHost?.let { host ->
                val service = it.wembleyService
                val wembleyDoneTime = it.created
                val done = success && wembleyDoneTime != null

                BigIPAddress(create("https://$host/$service"), wembleyDoneTime, done, "")
            }

            listOf(route).addIfNotNull(bigIpRoute)
        }
        val websealAddresses = findWebsealAddresses(services, namespace)

        return serviceAddresses.addIfNotNull(routeAddresses).addIfNotNull(websealAddresses)
    }

    private suspend fun findWebsealAddresses(
        services: List<Service>,
        namespace: String,
    ): List<WebSealAddress> = services.filter { it.marjoryDone != null }.mapNotNull {
        openshiftClient.getRouteOrNull(
            newRoute {
                metadata {
                    this.namespace = namespace
                    this.name = "${it.metadata.name}-webseal"
                }
            }
        )?.let {
            val status = it.status.ingress.first().conditions.first()
            val success = status.success() && it.marjoryOpen

            WebSealAddress(
                url = create("https://${it.spec.host}"),
                time = it.marjoryDone,
                available = success,
                status = status.reason,
                roles = it.marjoryRoles
            )
        }
    }
}
