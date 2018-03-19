package no.skatteetaten.aurora.mokey.model

data class ApplicationId(val name: String, val environment: Environment) {
    override fun toString(): String {
        return listOf(environment.affiliation, environment.name, name).joinToString("/")
    }
}
