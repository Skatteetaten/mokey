package no.skatteetaten.aurora.openshift.webclient

import org.springframework.web.reactive.function.client.WebClient

fun WebClient.RequestHeadersUriSpec<*>.applicationDeployment(namespace: String? = null): WebClient.RequestHeadersSpec<*> {
    val path = OpenShiftApiGroup.APPLICATIONDEPLOYMENT.path(namespace)
    return this.uri(path)
}