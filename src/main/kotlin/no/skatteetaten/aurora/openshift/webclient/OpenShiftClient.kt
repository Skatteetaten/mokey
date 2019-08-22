package no.skatteetaten.aurora.openshift.webclient

import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.ProjectList
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.api.model.RouteList
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentList
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.POD
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.REPLICATIONCONTROLLER
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.SERVICE
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.APPLICATIONDEPLOYMENT
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.DEPLOYMENTCONFIG
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.IMAGESTREAMTAG
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.PROJECT
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.ROUTE
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

class OpenShiftClient(val webClient: WebClient) {

    fun deploymentConfig(namespace: String, name: String): Mono<DeploymentConfig> {
        return webClient
            .get()
            .openShiftResource(DEPLOYMENTCONFIG, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployment(namespace: String, name: String): Mono<ApplicationDeployment> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployments(namespace: String): Mono<ApplicationDeploymentList> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace)
            .retrieve()
            .bodyToMono()
    }

    fun route(namespace: String, name: String): Mono<Route> {
        return webClient
            .get()
            .openShiftResource(ROUTE, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun routes(namespace: String, labelMap: Map<String, String>): Mono<RouteList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = ROUTE, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun services(namespace: String?, labelMap: Map<String, String>): Mono<ServiceList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = SERVICE, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun pods(namespace: String, labelMap: Map<String, String>): Mono<PodList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = POD, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun replicationController(namespace: String, name: String): Mono<ReplicationController> {
        return webClient
            .get()
            .openShiftResource(REPLICATIONCONTROLLER, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): Mono<ImageStreamTag> {
        return webClient
            .get()
            .openShiftResource(IMAGESTREAMTAG, namespace, "$name:$tag")
            .retrieve()
            .bodyToMono()
    }

    fun projects(): Mono<ProjectList> {
        return webClient
            .get()
            .openShiftResource(PROJECT)
            .retrieve()
            .bodyToMono()
    }
}

fun WebClient.RequestHeadersUriSpec<*>.openShiftResource(
    apiGroup: ApiGroup,
    namespace: String? = null,
    name: String? = null,
    labels: Map<String, String> = emptyMap()
): WebClient.RequestHeadersSpec<*> {
    val path = apiGroup.path(namespace, name)

    return if (labels.isEmpty()) {
        this.uri(path)
    } else {
        this.uri {
            it.path(path).queryParam("labelSelector", apiGroup.labelSelector(labels)).build()
        }
    }
}
