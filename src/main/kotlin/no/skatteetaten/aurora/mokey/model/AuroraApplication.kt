package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.databind.JsonNode
import io.fabric8.kubernetes.api.model.Time


data class AuroraApplication(
        val name: String,
        val namespace: String,
        val affiliation: String? = null,
        val sprocketDone: String? = null,
        val targetReplicas: Int = 0,
        val availableReplicas: Int = 0,
        val managementPath: String? = null,
        val deploymentPhase: String? = null,
        val routeUrl: String? = null,
        val pods: List<AuroraPod>,
        val imageStream: AuroraImageStream? = null
)

data class AuroraImageStream(val isTag: String,
                             val registryUrl: String,
                             val group: String,
                             val name: String,
                             val tag: String)

data class AuroraPod(
        val name: String,
        val status: String,
        val restartCount: Int = 0,
        val podIP: String,
        val isReady: Boolean = false,
        val startTime: Time,
        val deployment: String?,
        val info: JsonNode?,
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
