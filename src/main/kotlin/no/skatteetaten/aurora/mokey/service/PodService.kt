package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.springframework.stereotype.Service

@Service
class PodService(val openshiftService: OpenShiftService,
                 val managementDataService: ManagementDataService) {

    fun getPodDetails(dc: DeploymentConfig): List<PodDetails> {

        val labelMap = dc.spec.selector.mapValues { it.value }
        return openshiftService.pods(dc.metadata.namespace, labelMap).map {
            val podIP = it.status.podIP ?: null
            val managementData = managementDataService.load(podIP, dc.managementPath)

            val status = it.status.containerStatuses.first()
            PodDetails(
                    OpenShiftPodExcerpt(
                            name = it.metadata.name,
                            status = it.status.phase,
                            restartCount = status.restartCount,
                            ready = status.ready,
                            podIP = podIP,
                            deployment = it.metadata.labels["deployment"],
                            startTime = it.status.startTime
                    ),
                    managementData
            )
        }
    }
}