package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.databind.JsonNode
import java.security.MessageDigest

data class PodDetails(
    val openShiftPodExcerpt: OpenShiftPodExcerpt,
    val links: Map<String, String>? = null,
    val info: JsonNode? = null
)

data class OpenShiftPodExcerpt(
    val name: String,
    val status: String,
    val restartCount: Int = 0,
    val ready: Boolean = false,
    val podIP: String,
    val startTime: String,
    val deployment: String?
)

data class ImageDetails(
    val registryUrl: String,
    val group: String,
    val name: String,
    val tag: String,
    val env: Map<String, String>? = null
)

data class ApplicationData(
    private val applicationId: ApplicationId,
    val deployTag: String,
    val name: String,
    val namespace: String,
    val affiliation: String?,
    val targetReplicas: Int,
    val availableReplicas: Int,
    val booberDeployId: String? = null,
    val deploymentPhase: String? = null,
    val managementPath: String? = null,
    val pods: List<PodDetails> = emptyList(),
    val imageStream: ImageDetails? = null,
    val sprocketDone: String? = null,
    val auroraVersion: String? = null
) {
    val id: String
        get() = applicationId.toString().sha256("apsldga019238")
}

data class ApplicationId(val name: String, val environment: Environment) {
    override fun toString(): String {
        return listOf(environment.affiliation, environment.name, name).joinToString("/")
    }
}

data class Environment(val name: String, val affiliation: String) {
    companion object {
        fun fromNamespace(namespace: String, affiliation: String? = null): Environment {
            val theAffiliation = affiliation ?: namespace.substringBefore("-")
            val name = namespace.replace("$theAffiliation-", "")
            return Environment(name, theAffiliation)
        }
    }
}

private fun String.sha256(salt: String): String {
    val HEX_CHARS = "0123456789ABCDEF"
    val bytes = MessageDigest
        .getInstance("SHA-256")
        .digest((this + salt).toByteArray())
    val result = StringBuilder(bytes.size * 2)
    bytes.forEach {
        val i = it.toInt()
        result.append(HEX_CHARS[i shr 4 and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }
    return result.toString()
}