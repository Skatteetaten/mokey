package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.mokey.model.DiscoveryResponse
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpoint
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.createEndpoint
import no.skatteetaten.aurora.mokey.model.missingResult
import org.springframework.stereotype.Service

@Service
class ManagementDataService(
    val client: OpenShiftManagementClient
) {

    suspend fun load(pod: Pod, endpointPath: String?): ManagementData {

        val (port, path) = try {
            assert(endpointPath != null && endpointPath.isNotBlank()) {
                "Management path is missing"
            }
            val port = endpointPath!!.substringBefore("/").removePrefix(":").toInt()
            val p = endpointPath.substringAfter("/")
            port to p
        } catch (e: Throwable) {
            return ManagementData(
                ManagementEndpointResult(
                    errorMessage = e.message,
                    endpointType = EndpointType.DISCOVERY,
                    resultCode = "ERROR_CONFIGURATION"
                )
            )
        }

        val discoveryEndpoint = ManagementEndpoint(pod, port, path, EndpointType.DISCOVERY)

        val discoveryResponse: ManagementEndpointResult<DiscoveryResponse> = client.getCachedOrFind(discoveryEndpoint)

        val discoveryResult = discoveryResponse.deserialized ?: return ManagementData((discoveryResponse))

        val info = discoveryResult.createEndpoint(pod, port, EndpointType.INFO)?.let {
            client.getCachedOrFind<InfoResponse>(it)
        } ?: EndpointType.INFO.missingResult()

        val env = discoveryResult.createEndpoint(pod, port, EndpointType.ENV)?.let {
            client.getCachedOrFind<JsonNode>(it)
        } ?: EndpointType.ENV.missingResult()

        val health = discoveryResult.createEndpoint(pod, port, EndpointType.HEALTH)?.let {
            parseHealthResult(client.findJsonResource(it))
        } ?: EndpointType.HEALTH.missingResult()

        return ManagementData(
            links = discoveryResponse,
            info = info,
            env = env,
            health = health
        )
    }

    private fun parseHealthResult(it: ManagementEndpointResult<JsonNode>): ManagementEndpointResult<JsonNode> {
        if (!it.isSuccess) {
            return it
        }
        val statusField = it.deserialized?.at("/status")
        if (statusField == null || statusField is MissingNode) {
            return ManagementEndpointResult(
                errorMessage = "Invalid format, does not contain status",
                endpointType = EndpointType.HEALTH,
                resultCode = "INVALID_FORMAT"
            )
        }

        try {
            HealthStatus.valueOf(statusField.textValue())
        } catch (e: Exception) {
            return ManagementEndpointResult(
                errorMessage = "Invalid format, status is not valid HealthStatus value",
                endpointType = EndpointType.HEALTH,
                resultCode = "INVALID_FORMAT"
            )
        }
        return it
    }
}
