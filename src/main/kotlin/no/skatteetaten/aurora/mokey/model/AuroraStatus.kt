package no.skatteetaten.aurora.mokey.model

// TODO: Should comment be renamed to reason?
data class AuroraStatus(val level: AuroraStatusLevel, val comment: String)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}
