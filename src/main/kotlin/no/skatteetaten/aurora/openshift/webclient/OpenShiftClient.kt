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
import io.fabric8.openshift.api.model.User
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
import no.skatteetaten.aurora.openshift.webclient.OpenShiftApiGroup.USER
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.time.Duration

private val logger = KotlinLogging.logger {}

class OpenShiftClient(private val serviceAccountWebClient: WebClient, private val userWebClient: WebClient) {

    fun deploymentConfig(namespace: String, name: String): Mono<DeploymentConfig> {
        return get()
            .openShiftResource(DEPLOYMENTCONFIG, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployment(namespace: String, name: String): Mono<ApplicationDeployment> {
        return get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployments(namespace: String): Mono<ApplicationDeploymentList> {
        return get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace)
            .retrieve()
            .bodyToMono()
    }

    fun route(namespace: String, name: String): Mono<Route> {
        return get()
            .openShiftResource(ROUTE, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun routes(namespace: String, labelMap: Map<String, String>): Mono<RouteList> {
        return get()
            .openShiftResource(apiGroup = ROUTE, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun services(namespace: String?, labelMap: Map<String, String>): Mono<ServiceList> {
        return get()
            .openShiftResource(apiGroup = SERVICE, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun pods(namespace: String, labelMap: Map<String, String>): Mono<PodList> {
        return get()
            .openShiftResource(apiGroup = POD, namespace = namespace, labels = labelMap)
            .retrieve()
            .bodyToMono()
    }

    fun replicationController(namespace: String, name: String): Mono<ReplicationController> {
        return get()
            .openShiftResource(REPLICATIONCONTROLLER, namespace, name)
            .retrieve()
            .bodyToMono()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): Mono<ImageStreamTag> {
        return get()
            .openShiftResource(IMAGESTREAMTAG, namespace, "$name:$tag")
            .retrieve()
            .bodyToMono()
    }

    fun project(name: String, token: String? = null): Mono<Project> {
        return get(token)
            .openShiftResource(apiGroup = PROJECT, name = name)
            .retrieve()
            .bodyToMono()
    }

    fun projects(token: String? = null): Mono<ProjectList> {
        return get(token)
            .openShiftResource(PROJECT)
            .retrieve()
            .bodyToMono()
    }

    fun selfSubjectAccessView(review: SelfSubjectAccessReview): Mono<SelfSubjectAccessReview> {
        return serviceAccountWebClient
            .post()
            .uri(SELFSUBJECTACCESSREVIEW.uri().expand())
            .body(BodyInserters.fromObject(review))
            .retrieve()
            .bodyToMono()
    }

    fun user(token: String): Mono<User> {
        return get(token)
            .uri(USER.uri().expand())
            .retrieve()
            .bodyToMono()
    }

    private fun get(token: String? = null) =
        token?.let {
            userWebClient.get().apply {
                this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            }
        } ?: serviceAccountWebClient.get()
}

fun WebClient.RequestHeadersUriSpec<*>.openShiftResource(
    apiGroup: ApiGroup,
    namespace: String? = null,
    name: String? = null,
    labels: Map<String, String> = emptyMap()
): WebClient.RequestHeadersSpec<*> {
    val uri = apiGroup.uri(namespace, name)
    return if (labels.isEmpty()) {
        this.uri(uri.template, uri.variables)
    } else {
        this.uri {
            it.path(uri.template).queryParam("labelSelector", apiGroup.labelSelector(labels)).build(uri.variables)
        }
    }
}

fun <T> Mono<T>.notFoundAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.NotFound -> {
            logger.info { "Resource not found: ${it.request?.method} ${it.request?.uri}" }
            Mono.empty()
        }
        is WebClientResponseException.Unauthorized -> {
            Mono.error(BadCredentialsException(it.localizedMessage, it))
        }
        else -> Mono.error(it)
    }
}

fun <T> Mono<T>.retryWithLog() = this.retryWhen(Retry.onlyIf<Mono<T>> {
    it.exception() !is WebClientResponseException.Unauthorized
}.exponentialBackoff(Duration.ofMillis(10), Duration.ofMillis(50)).retryMax(3))

fun <T> Mono<T>.blockForResource() = this.notFoundAsEmpty().retryWithLog().block()

fun <T : HasMetadata?> Mono<out KubernetesResourceList<T>>.blockForList(): List<T> =
    this.blockForResource()?.items ?: emptyList()
