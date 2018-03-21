package no.skatteetaten.aurora.mokey.model

data class AuroraStatus(val level: AuroraStatusLevel, val comment: String)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(4),
    UNKNOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}
