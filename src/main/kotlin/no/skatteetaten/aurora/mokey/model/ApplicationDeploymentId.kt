package no.skatteetaten.aurora.mokey.model

data class ApplicationDeploymentId(val name: String, val environment: Environment) {
    override fun toString(): String {
        return listOf(environment.affiliation, environment.name, name).joinToString(separator)
    }

    companion object {
        val separator = "::"

        fun fromString(id: String): ApplicationDeploymentId {
            val (affiliation, environmentName, name) = id.split(separator)
            return ApplicationDeploymentId(name, Environment(environmentName, affiliation))
        }
    }
}
