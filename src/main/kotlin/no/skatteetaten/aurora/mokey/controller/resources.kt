package no.skatteetaten.aurora.mokey.controller

data class Application(
    val id: String,
    val affiliation: String?,
    val environment: String,
    val name: String,
    val status: AuroraStatus,
    val version: Version
)

data class AuroraStatus(val level: AuroraStatusLevel, val comment: String)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(4),
    UNKNOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}

data class Version(val deployTag: String?, val auroraVersion: String?)

data class ApplicationDetails(
    val application: Application
)