package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import org.springframework.stereotype.Service

@Service
class ManagementDataService(val managementEndpointFactory: ManagementEndpointFactory) {

    fun load(hostAddress: String?, endpointPath: String?): ManagementData {

        if (hostAddress.isNullOrBlank()) return ManagementData.withConfigError("Host address is missing")
        if (endpointPath.isNullOrBlank()) return ManagementData.withConfigError("Management Path is missing")

        val managementUrl = "http://$hostAddress$endpointPath"
        return load(managementUrl)
    }

    fun load(url: String): ManagementData {
        val managementEndpoint = try {
            managementEndpointFactory.create(url)
        } catch (e: ManagementEndpointException) {
            val error = ManagementEndpointError("Error when contacting management endpoint", e.endpoint, e.errorCode, e.cause?.message)
            return ManagementData(errors = listOf(error))
        }

        val errors = mutableListOf<ManagementEndpointError>()
        val info = try {
            managementEndpoint.getInfoEndpointResponse()
        } catch (e: ManagementEndpointException) {
            errors += ManagementEndpointError("Error when contacting info endpoint", e.endpoint, e.errorCode, e.cause?.message)
            null
        }

        val health = try {
            managementEndpoint.getHealthEndpointResponse()
        } catch (e: ManagementEndpointException) {
            errors += ManagementEndpointError("Error when contacting health endpoint", e.endpoint, e.errorCode, e.cause?.message)
            null
        }

        return ManagementData(managementEndpoint.links, info, health, errors)
    }
}