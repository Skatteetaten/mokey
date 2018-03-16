package no.skatteetaten.aurora.mokey.model

data class PodDetails(
    val openShiftPodExcerpt: OpenShiftPodExcerpt,
    val links: Map<String, String>? = null
)

data class OpenShiftPodExcerpt(
    val name: String,
    val status: String,
    val restartCount: Int = 0,
    val ready: Boolean = false,
    val podIP: String,
    val startTime: String,
    val deployment: String?
)

data class ImageDetails(
    val registryUrl: String,
    val group: String,
    val name: String,
    val tag: String,
    val env: Map<String, String>? = null
)

data class ApplicationData(
    val name: String,
    val namespace: String,
    val deployTag: String,
    val affiliation: String,
    val targetReplicas: Int,
    val availableReplicas: Int,
    val booberDeployId: String? = null,
    val deploymentPhase: String? = null,
    val managementPath: String? = null,
    val pods: List<PodDetails> = emptyList(),
    val imageStream: ImageDetails? = null,
    val sprocketDone: String? = null,
    val auroraVersion: String? = null
)

data class ApplicationId(val name: String, val environment: Environment) {
    override fun toString(): String {
        return "${environment.affiliation}/${environment.name}/$name"
    }
}

data class Environment(val name: String, val affiliation: String) {
    companion object {
        fun fromNamespace(namespace: String): Environment {
            val affiliation = namespace.substringBefore("-")
            val name = namespace.substringAfter("-")
            return Environment(name, affiliation)
        }
    }
}