package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ManagementData
import org.springframework.stereotype.Service

@Service
class ManagementDataService(val managementEndpointFactory: ManagementEndpointFactory) {

    fun load(hostAddress: String?, endpointPath: String?): ManagementData {

        if (hostAddress.isNullOrBlank()) return ManagementData.withError("Host address is missing")
        if (endpointPath.isNullOrBlank()) return ManagementData.withError("Management Path is missing")

        val managementUrl = "http://$hostAddress$endpointPath"
        return load(managementUrl)
    }

    fun load(url: String): ManagementData {
        val managementEndpoint = try {
            managementEndpointFactory.create(url)
        } catch (e: Exception) {
            return ManagementData.withError("Error while contacting Management Endpoint. ${e.message}")
        }

        val info = try {
            managementEndpoint.getInfoEndpointResponse()
        } catch (e: Exception) {
            return ManagementData.withError("Error while contacting Info Endpoint. ${e.message}")
        }

        val health = try {
            managementEndpoint.getHealthEndpointResponse()
        } catch (e: Exception) {
            return ManagementData.withError("Error while contacting Health Endpoint. ${e.message}")
        }

        return ManagementData(managementEndpoint.links, info, health)
    }
}