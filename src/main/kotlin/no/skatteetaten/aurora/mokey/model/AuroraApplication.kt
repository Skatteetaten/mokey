package no.skatteetaten.aurora.mokey.model

data class AuroraApplication (
    val id: ApplicationId,
    val status: String,
    val version: Version
)

data class ApplicationId(val name: String, val environment: Environment)

data class Environment(val name: String, val affiliation: String)

data class Version(val deployTag: String, val auroraVersion: String)

data class AuroraStatus2(val code: String, val comment: String)

data class ApplicationDetails(
    val application: AuroraApplication,
    val auroraStatus: AuroraStatus2,
    val pods: List<PodDetails> = listOf(),
    val imageDetails: ImageDetails? = null,
    val deployment: OpenShiftDeploymentExcerpt,

    val routeUrl: String? = null
)

data class ImageDetails(
    val buildTime: String,

    val registryUrl: String,
    val group: String,
    val name: String,
    val tag: String,
    val env: Map<String, String>? = null
)

data class PodDetails(
    val openShiftPodExcerpt: OpenShiftPodExcerpt,
    val links: Map<String, String>? = null
)

data class OpenShiftDeploymentExcerpt (
    val booberDeployId: String? = null,
    val managementPath: String? = null,
    val sprocketDone: String? = null,
    val targetReplicas: Int = 0,
    val availableReplicas: Int = 0,
    val deploymentPhase: String? = null
)

data class OpenShiftPodExcerpt(
    val name: String,
    val status: String,
    val restartCount: Int = 0,
    val ready: Boolean = false,
    val startTime: String,
    val deployment: String?
)


data class AuroraStatus(val level: AuroraStatusLevel, val comment: String? = "") {


    enum class AuroraStatusLevel(val level: Int) {
        DOWN(4),
        UNKNOWN(3),
        OBSERVE(2),
        OFF(1),
        HEALTHY(0)
    }

    companion object {

        //TODO:config
        val AVERAGE_RESTART_OBSERVE_THRESHOLD = 20
        val AVERAGE_RESTART_ERROR_THRESHOLD = 100
        val DIFFERENT_DEPLOYMENT_HOUR_THRESHOLD = 2

        fun fromApplicationStatus(status: String, message: String): AuroraStatus {

            var level = AuroraStatusLevel.UNKNOWN

            if ("UP".equals(status, ignoreCase = true)) {
                level = AuroraStatusLevel.HEALTHY
            }

            if ("OBSERVE".equals(status, ignoreCase = true)) {
                level = AuroraStatusLevel.OBSERVE
            }

            if ("UNKNOWN".equals(status, ignoreCase = true)) {
                level = AuroraStatusLevel.UNKNOWN
            }

            if ("OUT_OF_SERVICE".equals(status, ignoreCase = true)) {
                level = AuroraStatusLevel.DOWN
            }

            if ("DOWN".equals(status, ignoreCase = true)) {
                level = AuroraStatusLevel.DOWN
            }

            return AuroraStatus(level, message)
        }
    }
}
data class Response(
        val success: Boolean = true,
        val message: String = "OK",
        val items: List<Any>,
        val count: Int = items.size
)
