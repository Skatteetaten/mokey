package no.skatteetaten.aurora.openshift.webclient

import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.*
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class OpenShiftClient(val webClient: WebClient) {

    fun dc(namespace: String, name: String): Mono<DeploymentConfig> {
        return webClient
            .get()
            .openShiftResource(DEPLOYMENTCONFIG, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun ad(namespace: String, name: String): Mono<ApplicationDeployment> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace, name)
            .retrieve()
            .bodyToMono()
    }
}

fun WebClient.RequestHeadersUriSpec<*>.openShiftResource(apiGroup: ApiGroup, namespace: String? = null, name: String? = null): WebClient.RequestHeadersSpec<*> {
    val path = apiGroup.path(namespace, name)
    return this.uri(path)
}
