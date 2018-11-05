package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ManagementData
import org.springframework.stereotype.Service

@Service
class ManagementDataService(val managementInterfaceFactory: ManagementInterfaceFactory) {

    fun load(hostAddress: String?, endpointPath: String?): ManagementData {
        val p = managementInterfaceFactory.create(hostAddress, endpointPath)

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