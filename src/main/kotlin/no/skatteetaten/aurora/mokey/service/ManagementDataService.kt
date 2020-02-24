package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.mokey.model.ManagementData
import org.springframework.stereotype.Service

@Service
class ManagementDataService(
    val managementInterfaceFactory: ManagementInterfaceFactory
) {

    fun load(pod: Pod, endpointPath: String?): ManagementData {
        val p = managementInterfaceFactory.create(pod, endpointPath)

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
