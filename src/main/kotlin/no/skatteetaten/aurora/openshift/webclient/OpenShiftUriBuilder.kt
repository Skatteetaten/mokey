package no.skatteetaten.aurora.openshift.webclient

import org.springframework.web.util.UriComponentsBuilder

data class OpenShiftUri(val template: String, val variables: Map<String, String?>) {
    fun expand() = UriComponentsBuilder.fromPath(template).buildAndExpand(variables).toUriString()
}

interface ApiGroup {
    fun uri(namespace: String? = null, name: String? = null): OpenShiftUri
    fun labelSelector(labels: Map<String, String> = emptyMap()) =
        labels.map { "${it.key}=${it.value}" }.joinToString(",")
}

enum class OpenShiftApiGroup(private val label: String, private val suffix: String = "") : ApiGroup {
    APPLICATIONDEPLOYMENT("skatteetaten.no/v1"),
    DEPLOYMENTCONFIG("apps"),
    ROUTE("route"),
    USER("user", "/~"),
    PROJECT("project"),
    IMAGESTREAMTAG("image");

    override fun uri(namespace: String?, name: String?): OpenShiftUri {
        val path = if (label.contains(".")) {
            "/apis/$label"
        } else {
            "/apis/$label.openshift.io/v1"
        }

        val ns = ns(namespace)
        val n = n(name)
        val uriTemplate = "$path$ns$kind$n$suffix"
        return OpenShiftUri(
            uriTemplate, mapOf(
                "namespace" to namespace,
                "kind" to "${this.name.toLowerCase()}s",
                "name" to name
            )
        )
    }
}

enum class KubernetesApiGroup(private val label: String) : ApiGroup {
    SERVICE("services"),
    POD("pods"),
    REPLICATIONCONTROLLER("replicationcontrollers"),
    IMAGESTREAMTAG("imagestreamtags"),
    SELFSUBJECTACCESSREVIEW("authorization.k8s.io");

    override fun uri(namespace: String?, name: String?): OpenShiftUri {
        val path = if (label.contains(".")) {
            "/apis/$label/v1"
        } else {
            "/api/v1"
        }

        val ns = ns(namespace)
        val uriTemplate = "$path$ns$kind"
        return OpenShiftUri(
            uriTemplate, mapOf(
                "namespace" to namespace,
                "kind" to "${this.name.toLowerCase()}s"
            )
        )
    }
}

private fun ns(namespace: String?) = namespace?.let { "/namespaces/{namespace}" } ?: ""
private fun n(name: String?) = name?.let { "/{name}" } ?: ""
private const val kind = "/{kind}"
