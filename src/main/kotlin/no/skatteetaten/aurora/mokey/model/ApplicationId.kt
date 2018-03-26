package no.skatteetaten.aurora.mokey.model

data class ApplicationId(val name: String, val environment: Environment) {
    override fun toString(): String {
        return listOf(environment.affiliation, environment.name, name).joinToString(separator)
    }

    companion object {
        val separator = "::"

        fun fromString(id: String): ApplicationId {
            val (affiliation, environmentName, name) = id.split(separator)
            return ApplicationId(name, Environment(environmentName, affiliation))
        }
    }
}
