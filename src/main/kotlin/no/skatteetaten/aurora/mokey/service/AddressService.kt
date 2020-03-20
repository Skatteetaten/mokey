package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.newObjectMeta
import no.skatteetaten.aurora.mokey.extensions.addIfNotNull
import no.skatteetaten.aurora.mokey.extensions.created
import no.skatteetaten.aurora.mokey.extensions.ensureStartWith
import no.skatteetaten.aurora.mokey.model.Address
import no.skatteetaten.aurora.mokey.model.RouteAddress
import no.skatteetaten.aurora.mokey.model.ServiceAddress
import java.net.URI

@org.springframework.stereotype.Service
class AddressService(
    val openshiftClient: OpenShiftServiceAccountClient
) {

    /*
     * TODO: Missing some details here
     */
    suspend fun getAddresses(namespace: String, name: String): List<Address> {

        val metadata = newObjectMeta {
            this.namespace = namespace
            this.labels = mapOf("app" to name)
        }
        val services = openshiftClient.getServices(metadata)
        val ingress = openshiftClient.getIngresses(metadata)

        val serviceAddresses = services.map { ServiceAddress(URI.create("http://${it.metadata.name}"), it.created) }

        val routeAddresses = ingress.flatMap {

            val rule = it.spec.rules.first()

            val host = rule.host

            val path = rule.http.paths.first().path?.let { path ->
                path.ensureStartWith("/")
            } ?: ""
            val success = true // TODO
            val protocol = "http" // TODO: Http

            val route = RouteAddress(URI.create("$protocol://${host}$path"), it.created, success, "OK")

            listOf(route)
        }

        return serviceAddresses.addIfNotNull(routeAddresses)
    }
}
