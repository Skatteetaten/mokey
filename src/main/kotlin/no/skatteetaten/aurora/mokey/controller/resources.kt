package no.skatteetaten.aurora.mokey.controller

data class ApplicationResource(
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

data class ApplicationDetailsResource(
        val application: ApplicationResource,
        val imageDetails: ImageDetailsResource,
        val podResources: List<PodResource>
)

data class ImageDetailsResource(
        val dockerImageReference: String?,
        val imageBuildTime: String?,
        val environmentVariables: Map<String, String>?
)

data class ManagementDataResource(
        val errorMessage: String?
)

data class PodResource(val managementData: ManagementDataResource)
