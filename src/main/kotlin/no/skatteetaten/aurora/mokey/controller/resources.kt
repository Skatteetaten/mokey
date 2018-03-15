package no.skatteetaten.aurora.mokey.controller

data class AuroraApplication(
    val id: ApplicationId,
    val status: AuroraStatus,
    val version: Version
)

data class ApplicationId(val name: String, val environment: Environment)

data class Environment(val name: String, val affiliation: String) {
    companion object {
        fun fromNamespace(namespace: String): Environment {
            val affiliation = namespace.substringBefore("-")
            val name = namespace.substringAfter("-")
            return Environment(name, affiliation)
        }
    }
}

data class AuroraStatus(val level: AuroraStatusLevel, val comment: String)

enum class AuroraStatusLevel(val level: Int) {
    DOWN(4),
    UNKNOWN(3),
    OBSERVE(2),
    OFF(1),
    HEALTHY(0)
}

data class Version(val deployTag: String?, val auroraVersion: String?)
