package no.skatteetaten.aurora.mokey.model

data class Environment(val name: String, val affiliation: String) {
    val namespace: String get() = if (name == affiliation) affiliation else "$affiliation-$name"

    companion object {
        fun fromNamespace(namespace: String, affiliation: String? = null): Environment {
            val theAffiliation = affiliation ?: namespace.substringBefore("-")
            val name = namespace.replaceFirst("$theAffiliation-", "")

            return Environment(name, theAffiliation)
        }
    }
}
