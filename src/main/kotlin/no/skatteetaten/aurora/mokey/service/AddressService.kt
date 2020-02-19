package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.Service
import java.net.URI
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
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

@org.springframework.stereotype.Service
class AddressService(
    @TargetClient(ClientTypes.SERVICE_ACCOUNT) val client: KubernetesCoroutinesClient
) {

    fun getAddresses(namespace: String, name: String): List<Address> {

        return runBlocking {
            val labels = mapOf("app" to name)
            val metadata = newObjectMeta {
                this.namespace = namespace
                this.labels = labels
            }
            val services = client.getMany(newService { this.metadata = metadata })
            val routes = client.getMany(newRoute { this.metadata = metadata })

            val serviceAddresses = services.map { ServiceAddress(URI.create("http://${it.metadata.name}"), it.created) }

            val routeAddresses = routes.flatMap {

                val path = it.spec.path?.let {
                    it.ensureStartWith("/")
                } ?: ""
                val status = it.status.ingress.first().conditions.first()
                val success = status.type == "Admitted" && status.status == "True"
                val protocol = if (it.spec.tls != null) {
                    "https"
                } else {
                    "http"
                }

                val route = RouteAddress(URI.create("$protocol://${it.spec.host}$path"), it.created, success, status.reason)

                val bigIpRoute = it.wembleyHost?.let { host ->
                    val service = it.wembleyService

                    val wembleyDoneTime = it.created
                    // TODO: SITS-120
                    // val wembleyDoneTime=it.wembleyDone

                    val done = success && wembleyDoneTime != null
                    BigIPAddress(URI.create("https://$host/$service"), wembleyDoneTime, done, "")
                }
                listOf(route).addIfNotNull(bigIpRoute)
            }

            val websealAddresses = findWebsealAddresses(services, namespace)

            serviceAddresses.addIfNotNull(routeAddresses).addIfNotNull(websealAddresses)
        }
    }

    private suspend fun findWebsealAddresses(services: List<Service>, namespace: String): List<WebSealAddress> {
        return services.filter { it.marjoryDone != null }.mapNotNull {
            client.getOrNull(newRoute {
                metadata {
                    this.namespace = namespace
                    this.name = "${it.metadata.name}-webseal"
                }
            })?.let {
                val status = it.status.ingress.first().conditions.first()
                val success = status.success() && it.marjoryOpen

                WebSealAddress(
                        url = URI.create("https://${it.spec.host}"),
                        time = it.marjoryDone,
                        available = success,
                        status = status.reason,
                        roles = it.marjoryRoles
                )
            }
        }
    }
}
