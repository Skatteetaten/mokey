package no.skatteetaten.aurora.mokey.model

import java.time.Instant

data class AuroraStatus(
    val level: AuroraStatusLevel,
    val reasons: List<StatusCheckReport> = listOf(),
    val reports: List<StatusCheckReport> = listOf(),
)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0),
}

data class StatusDescription(val ok: String, val failed: String)

abstract class StatusCheck(val description: StatusDescription, val failLevel: AuroraStatusLevel) {
    open val isOverridingAuroraStatus = false
    abstract fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean
    val name
        get() = this::class.simpleName ?: ""
}

data class StatusCheckResult(val statusCheck: StatusCheck, val hasFailed: Boolean) {
    val description: String
        get() = if (hasFailed) statusCheck.description.failed else statusCheck.description.ok
}

data class StatusCheckReport(
    val name: String,
    val description: String,
    val failLevel: AuroraStatusLevel,
    val hasFailed: Boolean,
)
