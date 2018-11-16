package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.springframework.stereotype.Service

@Service
class PodService(
    val openShiftService: OpenShiftService,
    val managementDataService: ManagementDataService
) {

    fun getPodDetails(applicationDeployment: ApplicationDeployment): List<PodDetails> {

        val pods = openShiftService.pods(applicationDeployment.metadata.namespace, applicationDeployment.spec.selector)
        return pods.map { pod: Pod ->
            val managementResult = managementDataService.load(pod.status.podIP, applicationDeployment.spec.managementPath)
            createPodDetails(pod, managementResult)
        }
    }

    companion object {
        fun createPodDetails(pod: Pod, managementResult: ManagementData): PodDetails {
            val containerStatus = pod.status.containerStatuses.firstOrNull()

            val podStateReason = containerStatus?.state?.waiting?.reason
            val podPhase = podStateReason ?: pod.status.phase

            return PodDetails(
                    OpenShiftPodExcerpt(
                            name = pod.metadata.name,
                            status = podPhase,
                            restartCount = containerStatus?.restartCount ?: 0,
                            ready = containerStatus?.ready ?: false,
                            podIP = pod.status.podIP,
                            deployment = pod.metadata.labels["deployment"],
                            startTime = pod.status.startTime
                    ),
                    managementResult
            )
        }
    }
}