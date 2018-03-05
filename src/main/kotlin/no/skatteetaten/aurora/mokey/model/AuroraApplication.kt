package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.databind.JsonNode


data class AuroraPublicApplication @JvmOverloads constructor(
        val name: String,
        val namespace: String,
        val affiliation: String,
        val auroraStatus: AuroraStatus,
        val deployTag: String,
        val auroraVersion: String
)


data class AuroraApplication @JvmOverloads constructor(
        val name: String,
        val namespace: String,
        val affiliation: String? = null,
        val deployTag: String? = null,
        val booberDeployId: String? = null,
        val sprocketDone: String? = null,
        val targetReplicas: Int = 0,
        val availableReplicas: Int = 0,
        val managementPath: String? = null,
        val deploymentPhase: String? = null,
        val routeUrl: String? = null,
        val pods: List<AuroraPod> = listOf(),
        val imageStream: AuroraImageStream? = null,
        val violationRules: Set<String> = emptySet()
)

data class AuroraImageStream(
                             val registryUrl: String,
                             val group: String,
                             val name: String,
                             val tag: String,
                             val localImage: Boolean = false,
                             val env: Map<String, String>? = null)

data class AuroraPod(
        val name: String,
        val status: String,
        val restartCount: Int = 0,
        val podIP: String?,
        val ready: Boolean = false,
        val startTime: String,
        val deployment: String?,
        val info: JsonNode? = null,
        val health: JsonNode? = null,
        val links: Map<String, String>? = null //cache
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
