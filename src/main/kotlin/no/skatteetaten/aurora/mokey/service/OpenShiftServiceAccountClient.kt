package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.newService
import com.fkorotkov.openshift.newRoute
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.openshift.api.model.Route
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
import org.springframework.stereotype.Service

@Service
class OpenShiftServiceAccountClient(
    @TargetClient(ClientTypes.SERVICE_ACCOUNT) val client: KubernetesCoroutinesClient
) {
    suspend fun getServices(metadata: ObjectMeta): List<io.fabric8.kubernetes.api.model.Service> =
        client.getMany(newService { this.metadata = metadata })

    suspend fun getRoutes(metadata: ObjectMeta): List<Route> =
        client.getMany(newRoute { this.metadata = metadata })

    suspend fun getRouteOrNull(route: Route) = client.getOrNull(route)
}