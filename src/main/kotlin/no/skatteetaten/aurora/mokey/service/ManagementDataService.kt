package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.model.ManagementResult
import org.springframework.stereotype.Service

@Service
class ManagementDataService(val managementEndpointFactory: ManagementEndpointFactory) {

    fun load(hostAddress: String?, endpointPath: String?): ManagementResult<ManagementData> {

        if (hostAddress.isNullOrBlank()) return ManagementResult(
                error = ManagementEndpointError(
                        message = "Host address is missing",
                        endpoint = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        if (endpointPath.isNullOrBlank()) return ManagementResult(
                error = ManagementEndpointError(
                        message = "Management Path is missing",
                        endpoint = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        return load("http://$hostAddress$endpointPath")
    }

    fun load(url: String): ManagementResult<ManagementData> {
        return try {
            tryLoad(url)
        } catch (e: Exception) {
            ManagementResult(error = ManagementEndpointError(
                    "Unexpected error while loading management data",
                    Endpoint.MANAGEMENT, "UNEXPECTED", e.message))
        }
    }

    private fun tryLoad(url: String): ManagementResult<ManagementData> {
        val managementEndpoint = try {
            managementEndpointFactory.create(url)
        } catch (e: ManagementEndpointException) {
            val error = ManagementEndpointError("Error when contacting management endpoint",
                    e.endpoint, e.errorCode, e.cause?.message)
            return ManagementResult(error = error)
        }

        val info = try {
            ManagementResult(value = managementEndpoint.getInfoEndpointResponse())
        } catch (e: ManagementEndpointException) {
            ManagementResult<JsonNode>(error = ManagementEndpointError("Error when contacting info endpoint",
                    e.endpoint, e.errorCode, e.cause?.message))
        }

        val health = try {
            ManagementResult(value = managementEndpoint.getHealthEndpointResponse())
        } catch (e: ManagementEndpointException) {
            ManagementResult<JsonNode>(error = ManagementEndpointError("Error when contacting health endpoint",
                    e.endpoint, e.errorCode, e.cause?.message))
        }

        return ManagementResult(value = ManagementData(managementEndpoint.links, info, health))
    }
}