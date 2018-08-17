package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.fabric8.kubernetes.api.model.ObjectMeta
import org.springframework.web.util.UriComponentsBuilder

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuroraApplicationInstance(
    val kind: String = "AuroraApplicationInstance",
    val metadata: ObjectMeta,
    val apiVersion: String = "skatteetaten.no/v1",
    val spec: ApplicationSpec
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationSpec(
    val applicationId: String,
    val applicationInstanceId: String,
    val splunkIndex: String? = null,
    val managementPath: String?,
    val releaseTo: String?,
    val deployTag: String?,
    val selector: Map<String, String>,
    val command: ApplicationCommand
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationCommand(
    val overrideFiles: Map<String, String>,
    val applicationId: ApplicationCommandId,
    val auroraConfig: AuroraConfigRef
) {

    fun createDeploymentSepcLink(host: String): String {
        val overridesQueryParam = overrideFiles.takeIf { it.isNotEmpty() }?.let {
            jacksonObjectMapper().writeValueAsString(it)
        }

        val uriComponents = UriComponentsBuilder.newInstance()
            .scheme("http").host(host)
            .pathSegment(
                "v1",
                "auroradeployspec",
                auroraConfig.name,
                applicationId.environment,
                applicationId.application
            )
            .queryParam("reference", auroraConfig.refName)

        overridesQueryParam?.let {
            uriComponents.queryParam("overrides", it)
        }
        return uriComponents.build().toUriString()
    }
}

data class AuroraConfigRef(
    val name: String,
    val refName: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AuroraApplicationInstanceList(
    val items: List<AuroraApplicationInstance> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApplicationCommandId(val environment: String, val application: String)
