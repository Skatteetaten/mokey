package no.skatteetaten.aurora.openshift.webclient

val kubernetesNonApiGroupResources = setOf(
    "namespace", "service", "configmap", "secret", "serviceaccount",
    "replicationcontroller", "persistentvolumeclaim", "pod"
)

fun findOpenShiftApiPrefix(apiVersion: String, kind: String) =
    if (apiVersion == "v1") {
        if (kind.toLowerCase() in kubernetesNonApiGroupResources) {
            "api"
        } else {
            "oapi"
        }
    } else {
        "apis"
    }

val apiGroups: Map<String, List<String>> =
    mapOf(
        "skatteetaten.no/v1" to listOf("applicationdeployment"),
        "apps.openshift.io/v1" to listOf("deploymentconfig", "deploymentrequest"),
        "route.openshift.io/v1" to listOf("route"),
        "user.openshift.io/v1" to listOf("user", "group"),
        "project.openshift.io/v1" to listOf("project", "projectrequest"),
        "template.openshift.io/v1" to listOf("template"),
        "image.openshift.io/v1" to listOf("imagestream", "imagestreamtag", "imagestreamimport"),
        "authorization.openshift.io/v1" to listOf("rolebinding"),
        "build.openshift.io/v1" to listOf("buildconfig")
    )

interface ApiGroup {
    fun path(namespace: String? = null, name: String? = null): String
    fun labelSelector(labels: Map<String, String> = emptyMap()) =
        labels.map { "${it.key}=${it.value}" }.joinToString(",")
}

private fun ns(namespace: String?) = namespace?.let {
    "/namespaces/$it"
} ?: ""

private fun n(name: String?) = name?.let {
    "/$it"
} ?: ""

enum class OpenShiftApiGroup(private val label: String, private val suffix: String = "") : ApiGroup {
    APPLICATIONDEPLOYMENT("skatteetaten.no/v1"),
    DEPLOYMENTCONFIG("apps"),
    ROUTE("route"),
    USER("user", "/~"),
    PROJECT("project"),
    IMAGESTREAMTAG("image");

    override fun path(namespace: String?, name: String?): String {
        val path = if (label.contains(".")) {
            "/apis/$label"
        } else {
            "/apis/$label.openshift.io/v1"
        }

        return "$path${ns(namespace)}/${this.name.toLowerCase()}s$suffix${n(name)}"
    }
}

enum class KubernetesApiGroup(private val label: String) : ApiGroup {
    SERVICE("services"),
    PODS("pods"),
    REPLICATIONCONTROLLER("replicationcontrollers"),
    IMAGESTREAMTAG("imagestreamtags"),
    APPLICATIONDEPLOYMENT("applicationdeployments");

    override fun path(namespace: String?, name: String?): String {
        if (namespace == null) {
            throw IllegalArgumentException("Namespace must be included for Kubernetes api groups")
        }

        return "/api/v1${ns(namespace)}$label${n(name)}"
    }
}