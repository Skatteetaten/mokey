package no.skatteetaten.aurora.openshift.webclient

import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class OpenShiftClient(val webClient: WebClient) {

    fun ad(namespace: String, name: String): Mono<ApplicationDeployment> {
        return webClient
            .get()
            .applicationDeployment()
            .retrieve()
            .bodyToMono()
    }
}
