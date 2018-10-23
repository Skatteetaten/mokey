package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.utils.Either
import no.skatteetaten.aurora.utils.Left
import no.skatteetaten.aurora.utils.Right
import org.springframework.stereotype.Service

typealias ManagementResult = Either<ManagementEndpointError, ManagementData>

@Service
class ManagementDataService(val managementEndpointFactory: ManagementEndpointFactory) {

    fun load(hostAddress: String?, endpointPath: String?): ManagementResult {

        if (hostAddress.isNullOrBlank()) return Left(
                ManagementEndpointError(
                        message = "Host address is missing",
                        endpointType = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        if (endpointPath.isNullOrBlank()) return Left(
                ManagementEndpointError(
                        message = "Management Path is missing",
                        endpointType = Endpoint.MANAGEMENT,
                        code = "CONFIGURATION"))

        return load("http://$hostAddress$endpointPath")
    }

    private fun load(url: String): ManagementResult {
        return try {
            val managementEndpoint = managementEndpointFactory.create(url)
            val infoEndpointResponse = managementEndpoint.getInfoEndpointResponse()
            val healthEndpointResponse = managementEndpoint.getHealthEndpointResponse()
            Right(ManagementData(managementEndpoint.links, infoEndpointResponse, healthEndpointResponse))
        } catch (e: ManagementEndpointException) {
            Left(ManagementEndpointError("Error while communicating with management endpoint",
                    e.endpoint, e.errorCode, e.cause?.message, url = url))
        } catch (e: Exception) {
            Left(ManagementEndpointError(
                    "Unexpected error while loading management data",
                    Endpoint.MANAGEMENT, "UNEXPECTED", e.message, url = url))
        }
    }
}