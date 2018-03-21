package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.model.Result
import org.springframework.stereotype.Service

typealias ManagementResult = Result<ManagementData, ManagementEndpointError>
typealias ManagementEdnpointResult = Result<JsonNode, ManagementEndpointError>

@Service
class ManagementDataService(val managementEndpointFactory: ManagementEndpointFactory) {


    fun load(hostAddress: String?, endpointPath: String?): ManagementResult {

        if (hostAddress.isNullOrBlank()) return Result(
                error = ManagementEndpointError(
                        message = "Host address is missing",
                        endpoint = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        if (endpointPath.isNullOrBlank()) return Result(
                error = ManagementEndpointError(
                        message = "Management Path is missing",
                        endpoint = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        return load("http://$hostAddress$endpointPath")
    }

    fun load(url: String): ManagementResult {
        return try {
            tryLoad(url)
        } catch (e: Exception) {
            Result(error = ManagementEndpointError(
                    "Unexpected error while loading management data",
                    Endpoint.MANAGEMENT, "UNEXPECTED", e.message))
        }
    }

    private fun tryLoad(url: String): ManagementResult {
        val managementEndpoint = try {
            managementEndpointFactory.create(url)
        } catch (e: ManagementEndpointException) {
            val error = ManagementEndpointError("Error when contacting management endpoint",
                    e.endpoint, e.errorCode, e.cause?.message)
            return Result(error = error)
        }

        //TODO: Add env endpoing

        //TODO: Marshal this a a Info class
        val info: ManagementEdnpointResult = try {
            Result(value = managementEndpoint.getInfoEndpointResponse())
        } catch (e: ManagementEndpointException) {
            Result(error = ManagementEndpointError("Error when contacting info endpoint",
                    e.endpoint, e.errorCode, e.cause?.message))
        }

        //TODO: Marshal this a a Health class not as jsonNode
        val health: ManagementEdnpointResult = try {
            Result(value = managementEndpoint.getHealthEndpointResponse())
        } catch (e: ManagementEndpointException) {
            Result(error = ManagementEndpointError("Error when contacting health endpoint",
                    e.endpoint, e.errorCode, e.cause?.message))
        }

        return ManagementResult(value = ManagementData(managementEndpoint.links, info, health))
    }
}