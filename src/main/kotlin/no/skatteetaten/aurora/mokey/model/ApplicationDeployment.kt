package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta

fun newApplicationDeployment(block: ApplicationDeployment.() -> Unit = {}): ApplicationDeployment {
    val instance = ApplicationDeployment()
    instance.block()
    return instance
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = ["apiVersion", "kind", "metadata", "spec"])
class ApplicationDeployment(
    var spec: ApplicationDeploymentSpec = ApplicationDeploymentSpec()
) : HasMetadata {

    private lateinit var metadata: ObjectMeta
    private var apiVersion: String = "skatteetaten.no/v1"

    override fun getMetadata() = metadata

    fun metadata(block: ObjectMeta.() -> Unit) {
        metadata = ObjectMeta()
        metadata.block()
    }

    override fun getKind(): String = "ApplicationDeployment"

    override fun getApiVersion() = apiVersion

    override fun setMetadata(data: ObjectMeta) {
        metadata = data
    }

    override fun setApiVersion(version: String) {
        this.apiVersion = version
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentSpec(
    val applicationId: String = "",
    val applicationName: String? = null,
    val applicationDeploymentId: String = "",
    val applicationDeploymentName: String? = null,
    val databases: List<String>? = null,
    val splunkIndex: String? = null,
    val managementPath: String? = null,
    val releaseTo: String? = null,
    val deployTag: String? = null,
    val selector: Map<String, String> = emptyMap(),
    val command: ApplicationDeploymentCommand = ApplicationDeploymentCommand(
        applicationDeploymentRef = ApplicationDeploymentRef("env", "app"),
            auroraConfig = AuroraConfigRef("demo", "master")
    ),
    val message: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentCommand(
    val overrideFiles: Map<String, String> = emptyMap(),
    val applicationDeploymentRef: ApplicationDeploymentRef,
    val auroraConfig: AuroraConfigRef
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuroraConfigRef(
    val name: String,
    val refName: String,
    val resolvedRef: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentList(
    val items: List<ApplicationDeployment> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentRef(val environment: String, val application: String)
