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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.retry.Retry
import java.time.Duration

private val logger = KotlinLogging.logger {}

abstract class AbstractOpenShiftClient(private val webClient: WebClient, private val token: String? = null) {

    fun deploymentConfig(namespace: String, name: String): Mono<DeploymentConfig> {
        return webClient
            .get()
            .openShiftResource(DEPLOYMENTCONFIG, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployment(namespace: String, name: String): Mono<ApplicationDeployment> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun applicationDeployments(namespace: String): Mono<ApplicationDeploymentList> {
        return webClient
            .get()
            .openShiftResource(APPLICATIONDEPLOYMENT, namespace)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun route(namespace: String, name: String): Mono<Route> {
        return webClient
            .get()
            .openShiftResource(ROUTE, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun routes(namespace: String, labelMap: Map<String, String>): Mono<RouteList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = ROUTE, namespace = namespace, labels = labelMap)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun services(namespace: String?, labelMap: Map<String, String>): Mono<ServiceList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = SERVICE, namespace = namespace, labels = labelMap)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun pods(namespace: String, labelMap: Map<String, String>): Mono<PodList> {
        return webClient
            .get()
            .openShiftResource(apiGroup = POD, namespace = namespace, labels = labelMap)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun replicationController(namespace: String, name: String): Mono<ReplicationController> {
        return webClient
            .get()
            .openShiftResource(REPLICATIONCONTROLLER, namespace, name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun imageStreamTag(namespace: String, name: String, tag: String): Mono<ImageStreamTag> {
        return webClient
            .get()
            .openShiftResource(IMAGESTREAMTAG, namespace, "$name:$tag")
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun project(name: String): Mono<Project> {
        return webClient
            .get()
            .openShiftResource(apiGroup = PROJECT, name = name)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun projects(): Mono<ProjectList> {
        return webClient
            .get()
            .openShiftResource(PROJECT)
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun selfSubjectAccessView(review: SelfSubjectAccessReview): Mono<SelfSubjectAccessReview> {
        val uri = SELFSUBJECTACCESSREVIEW.uri()
        return webClient
            .post()
            .uri(uri.template, uri.variables)
            .body(BodyInserters.fromObject(review))
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    fun user(): Mono<User> {
        return webClient
            .get()
            .uri(USER.uri().expand())
            .bearerToken(token)
            .retrieve()
            .bodyToMono()
    }

    private fun WebClient.RequestHeadersSpec<*>.bearerToken(token: String?) =
        token?.let {
            this.header(HttpHeaders.AUTHORIZATION, "Bearer $token")
        } ?: this

    private fun WebClient.RequestHeadersUriSpec<*>.openShiftResource(
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
}

class OpenShiftServiceAccountClient(webClient: WebClient) : AbstractOpenShiftClient(webClient)
class OpenShiftUserTokenClient(token: String, webClient: WebClient) : AbstractOpenShiftClient(webClient, token)

class OpenShiftClient(private val webClient: WebClient) {
    private val openShiftServiceAccountClient = OpenShiftServiceAccountClient(webClient)
    fun serviceAccount() = openShiftServiceAccountClient

    fun userToken(token: String = getUserToken()) = OpenShiftUserTokenClient(token, webClient)

    private fun getUserToken() =
        (SecurityContextHolder.getContext().authentication.principal as no.skatteetaten.aurora.mokey.controller.security.User).token
}

fun <T> Mono<T>.retryWithLog(retryFirstInMs: Long, retryMaxInMs: Long) =
    this.retryWhen(Retry.onlyIf<Mono<T>> {
        if (it.iteration() == 3L) {
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

        it.exception() !is WebClientResponseException.Unauthorized
    }.exponentialBackoff(Duration.ofMillis(retryFirstInMs), Duration.ofMillis(retryMaxInMs)).retryMax(3))

fun <T> Mono<T>.notFoundAsEmpty() = this.onErrorResume {
    when (it) {
        is WebClientResponseException.NotFound -> {
            logger.info { "Resource not found: ${it.request?.method} ${it.request?.uri}" }
            Mono.empty()
        }
        else -> Mono.error(it)
    }
}

private const val defaultFirstRetryInMs: Long = 100
private const val defaultMaxRetryInMs: Long = 2000

fun <T> Mono<T>.blockForResource(
    retryFirstInMs: Long = defaultFirstRetryInMs,
    retryMaxInMs: Long = defaultMaxRetryInMs
) =
    this.notFoundAsEmpty().retryWithLog(retryFirstInMs, retryMaxInMs).block()

fun <T : HasMetadata?> Mono<out KubernetesResourceList<T>>.blockForList(
    retryFirstInMs: Long = defaultFirstRetryInMs,
    retryMaxInMs: Long = defaultMaxRetryInMs
): List<T> = this.blockForResource(retryFirstInMs, retryMaxInMs)?.items ?: emptyList()
