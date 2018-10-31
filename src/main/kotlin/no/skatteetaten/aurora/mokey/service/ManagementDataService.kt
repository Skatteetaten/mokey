package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.springframework.stereotype.Service

@Service
class ManagementDataService(val managementInterfaceFactory: ManagementInterfaceFactory) {

    fun load(hostAddress: String?, endpointPath: String?): ManagementData {

        if (hostAddress.isNullOrBlank()) return ManagementData(
                links = ManagementEndpointResult(
                    textResponse = "Failed to invoke management endpoint",
                    rootCause = "Host address is missing",
                    endpointType = Endpoint.DISCOVERY,
                    code = "ERROR_CONFIGURATION"
                ))

        if (endpointPath.isNullOrBlank()) return ManagementData(
                links = ManagementEndpointResult(
                    textResponse = "Failed to invoke management endpoint",
                    rootCause = "Management path is missing",
                    endpointType = Endpoint.DISCOVERY,
                    code = "ERROR_CONFIGURATION"
                ))

        return load("http://$hostAddress$endpointPath")
    }

    private fun load(url: String): ManagementData {
        val p = managementInterfaceFactory.create(url)

        return p.first?.let { mgmtInterface ->
            ManagementData(
                    links = p.second,
                    info = mgmtInterface.getInfoEndpointResult(),
                    env = mgmtInterface.getEnvEndpointResult(),
                    health = mgmtInterface.getHealthEndpointResult()
            )
        } ?: ManagementData(links = p.second)
    }
}