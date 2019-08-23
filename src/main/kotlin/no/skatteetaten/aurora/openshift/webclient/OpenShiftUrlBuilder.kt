package no.skatteetaten.aurora.openshift.webclient

interface ApiGroup {
    fun path(namespace: String? = null, name: String? = null): String
    fun labelSelector(labels: Map<String, String> = emptyMap()) =
        labels.map { "${it.key}=${it.value}" }.joinToString(",")
}

private fun ns(namespace: String?) = namespace?.let { "/namespaces/$it" } ?: ""

private fun n(name: String?) = name?.let { "/$it" } ?: ""

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
    POD("pods"),
    REPLICATIONCONTROLLER("replicationcontrollers"),
    IMAGESTREAMTAG("imagestreamtags"),
    SELFSUBJECTACCESSREVIEW("authorization.k8s.io");

    override fun path(namespace: String?, name: String?): String {
        val path = if (label.contains(".")) {
            "/apis/$label/v1"
        } else {
            "/api/v1"
        }

        return "$path${ns(namespace)}/${this.name.toLowerCase()}s"
    }
}