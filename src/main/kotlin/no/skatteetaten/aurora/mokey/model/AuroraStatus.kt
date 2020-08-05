package no.skatteetaten.aurora.mokey.model

import no.skatteetaten.aurora.mokey.service.auroraStatus.averageRestarts
import java.time.Instant

data class AuroraStatus(
    val level: AuroraStatusLevel,
    val reasons: List<StatusCheckReport> = listOf(),
    val reports: List<StatusCheckReport> = listOf()
)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}

val emptyFn: (Map<String, String>) -> String = { context: Map<String, String> -> "" }

data class StatusDescription(
    val ok: String,
    val failed: String? = "",
    val failedFn: (context: Map<String, String>) -> String = emptyFn
)

data class StatusCheckContext(
    val scaledDown: String?,
    val averageRestarts: Int,
    val readyPods: Int,
    val observePods: Int,
    val errorPods: Int,
    val availableReplicas: Int,
    val targetReplicas: Int
)

abstract class StatusCheck(val description: StatusDescription, val failLevel: AuroraStatusLevel) {
    open val isOverridingAuroraStatus = false
    abstract fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean
    open fun generateContext(app: DeployDetails, pods: List<PodDetails>, time: Instant): Map<String, String> {

        val context = StatusCheckContext(
           scaledDown = app.scaledDown,
            targetReplicas = app.targetReplicas,
            availableReplicas = app.availableReplicas,
            averageRestarts = pods.averageRestarts(),


        )
        return emptyMap()
    }

    val name
        get() = this::class.simpleName ?: ""
}

data class StatusCheckResult(
    val statusCheck: StatusCheck,
    val hasFailed: Boolean,
    val context: Map<String, String> = emptyMap()
) {
    val description: String
        get() = if (hasFailed) {
            if (context.isNotEmpty()) {
                statusCheck.description.failedFn(context)
            } else {
                statusCheck.description.failed!!
            }
        } else statusCheck.description.ok
}

data class StatusCheckReport(
    val name: String,
    val description: String,
    val failLevel: AuroraStatusLevel,
    val hasFailed: Boolean
)
