package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import io.fabric8.kubernetes.api.model.ObjectMeta

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeployment(
    val kind: String = "ApplicationDeployment",
    val metadata: ObjectMeta,
    val apiVersion: String = "skatteetaten.no/v1",
    val deploymentSpec: ApplicationDeploymentSpec
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentSpec(
    val applicationId: String,
    val applicationInstanceId: String,
    val splunkIndex: String? = null,
    val managementPath: String?,
    val releaseTo: String?,
    val deployTag: String?,
    val selector: Map<String, String>,
    val deploymentCommand: ApplicationDeploymentCommand
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentCommand(
    val applicationId: ApplicationCommandId,
    val auroraConfig: AuroraConfigRef,
    val overrideFiles: Map<String, String> = emptyMap()
)

data class AuroraConfigRef(
    val name: String,
    val refName: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationDeploymentList(
    val items: List<ApplicationDeployment> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationCommandId(val environment: String, val application: String)
