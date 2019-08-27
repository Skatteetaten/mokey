package no.skatteetaten.aurora.openshift.webclient

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.KubernetesResourceList
import io.fabric8.kubernetes.api.model.PodList
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.ServiceList
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.ImageStreamTag
import io.fabric8.openshift.api.model.Project
import io.fabric8.openshift.api.model.ProjectList
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.api.model.RouteList
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentList
import no.skatteetaten.aurora.mokey.model.SelfSubjectAccessReview
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.POD
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.REPLICATIONCONTROLLER
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.SELFSUBJECTACCESSREVIEW
import no.skatteetaten.aurora.openshift.webclient.KubernetesApiGroup.SERVICE
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.APPLICATIONDEPLOYMENT
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.DEPLOYMENTCONFIG
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.IMAGESTREAMTAG
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.PROJECT
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.ROUTE
import org.springframework.http.HttpHeaders
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.retryExponentialBackoff
import java.time.Duration

private val logger = KotlinLogging.logger {}

class OpenShiftClient(private val serviceAccountWebClient: WebClient, private val userWebClient: WebClient) {

    fun deploymentConfig(namespace: String, name: String): Mono<DeploymentConfig> {
        return client()
            .get()
            .openShiftResource(DEPLOYMENTCONFIG, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployment(namespace: String, name: String): Mono<ApplicationDeployment> {
        return client()
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployments(namespace: String): Mono<ApplicationDeploymentList> {
        return client()
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace)
            .retrieve()
            .bodyToMono()
    }

    fun route(namespace: String, name: String): Mono<Route> {
        return client()
            .get()
            .openShiftResource(ROUTE, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun routes(namespace: String, labelMap: Map<String, String>): Mono<RouteList> {
        return client()
            .get()
            .openShiftResource(apiGroup = ROUTE, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun services(namespace: String?, labelMap: Map<String, String>): Mono<ServiceList> {
        return serviceAccountWebClient
            .get()
            .openShiftResource(apiGroup = SERVICE, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun pods(namespace: String, labelMap: Map<String, String>): Mono<PodList> {
        return client()
            .get()
            .openShiftResource(apiGroup = POD, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun replicationController(namespace: String, name: String): Mono<ReplicationController> {
        return client()
            .get()
            .openShiftResource(REPLICATIONCONTROLLER, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): Mono<ImageStreamTag> {
        return client()
            .get()
            .openShiftResource(IMAGESTREAMTAG, namespace, "$name:$tag")
            .retrieve()
            .bodyToMono()
    }

    fun project(name: String, token: String? = null): Mono<Project> {
        val request = client(token).get()
        token?.let {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer $it")
        }

        return request
            .openShiftResource(apiGroup = PROJECT, name = name)
            .retrieve()
            .bodyToMono()
    }

    fun projects(token: String? = null): Mono<ProjectList> {
        val request = client(token).get()
        token?.let {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer $it")
        }

        return request
            .openShiftResource(PROJECT)
            .retrieve()
            .bodyToMono()
    }

    fun selfSubjectAccessView(review: SelfSubjectAccessReview): Mono<SelfSubjectAccessReview> {
        return client()
            .post()
            .uri(SELFSUBJECTACCESSREVIEW.path())
            .body(BodyInserters.fromObject(review))
            .retrieve()
            .bodyToMono()
    }

    private fun client(token: String? = null) = if (token == null) {
        serviceAccountWebClient
    } else {
        userWebClient
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

fun <T> Mono<T>.notFoundAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.NotFound -> {
            logger.info { "Resource not found: ${it.request?.method} ${it.request?.uri}" }
            Mono.empty()
        }
        else -> Mono.error(it)
    }
}

fun <T> Mono<T>.retryWithLog() = this.retryExponentialBackoff(3, Duration.ofMillis(10)) {
    logger.info {
        val e = it.exception()
        val msg = "Retrying failed request, ${e.message}"
        if (e is WebClientResponseException) {
            "$msg, ${e.request?.method} ${e.request?.uri}"
        } else {
            msg
        }
    }
}

fun <T> Mono<T>.blockForResource() = this.notFoundAsEmpty().retryWithLog().block()

fun <T : HasMetadata?> Mono<out KubernetesResourceList<T>>.blockForList(): List<T> =
    this.blockForResource()?.items ?: emptyList()
