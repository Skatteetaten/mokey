package no.skatteetaten.aurora.mokey.model

import java.time.Instant

data class AuroraStatus(
    val level: AuroraStatusLevel,
    val comment: String = "",
    val description: String = "",
    val reports: List<StatusCheckReport> = listOf()
)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}

abstract class StatusCheck(val description: String, val failLevel: AuroraStatusLevel) {
    abstract fun isFailing(app: DeployDetails, pods: List<PodDetails>, time: Instant): Boolean
    val name
        get() = this::class.simpleName ?: ""
}

data class StatusCheckResult(val statusCheck: StatusCheck, val hasFailed: Boolean)

data class StatusCheckReport(
    val name: String,
    val description: String,
    val failLevel: AuroraStatusLevel,
    val hasFailed: Boolean
)
