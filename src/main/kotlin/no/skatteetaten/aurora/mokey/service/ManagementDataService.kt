package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.mokey.service.Endpoint.MANAGEMENT
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.Left
import no.skatteetaten.aurora.utils.Right
import org.springframework.stereotype.Service

typealias ManagementResult = Either<ManagementEndpointError, ManagementData>
typealias ManagementEdnpointResult = Either<ManagementEndpointError, JsonNode>

@Service
class ManagementDataService(val managementEndpointFactory: ManagementEndpointFactory) {


    fun load(hostAddress: String?, endpointPath: String?): ManagementResult {

        if (hostAddress.isNullOrBlank()) return Left(
                ManagementEndpointError(
                        message = "Host address is missing",
                        endpoint = MANAGEMENT,
                        code = "CONFIGURATION"))

        if (endpointPath.isNullOrBlank()) return Left(
                ManagementEndpointError(
                        message = "Management Path is missing",
                        endpoint = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        return load("http://$hostAddress$endpointPath")
    }

    fun load(url: String): ManagementResult {
        return try {
            tryLoad(url)
        } catch (e: Exception) {
            Left(ManagementEndpointError(
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
            return Left(error)
        }

        //TODO: Add env endpoing

        //TODO: Marshal this a a Info class
        val info: ManagementEdnpointResult = try {
            Right(managementEndpoint.getInfoEndpointResponse())
        } catch (e: ManagementEndpointException) {
            Left(ManagementEndpointError("Error when contacting info endpoint",
                    e.endpoint, e.errorCode, e.cause?.message))
        }

        //TODO: Marshal this a a Health class not as jsonNode
        val health: ManagementEdnpointResult = try {
            Right(managementEndpoint.getHealthEndpointResponse())
        } catch (e: ManagementEndpointException) {
            Left(ManagementEndpointError("Error when contacting health endpoint",
                    e.endpoint, e.errorCode, e.cause?.message))
        }

        return Right(ManagementData(managementEndpoint.links, info, health))
    }
}