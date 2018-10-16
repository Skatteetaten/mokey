package no.skatteetaten.aurora.mokey.model

data class AuroraStatus(val level: AuroraStatusLevel, val comment: String, val statuses: List<HealthStatusDetail> = emptyList())

enum class AuroraStatusLevel(val level: Int) {
    DOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}

data class HealthStatusDetail(val level: AuroraStatusLevel, val comment: String, val ref: String? = null)
