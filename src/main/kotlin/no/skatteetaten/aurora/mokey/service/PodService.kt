package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.extensions.managementPath
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.springframework.stereotype.Service

@Service
class PodService(
    val openShiftService: OpenShiftService,
    val managementDataService: ManagementDataService
) {

    fun getPodDetails(dc: DeploymentConfig): List<PodDetails> {

        val pods = openShiftService.pods(dc.metadata.namespace, dc.spec.selector)
        return pods.map { pod: Pod ->
            val managementResult = managementDataService.load(pod.status.podIP, dc.managementPath)
            createPodDetails(pod, managementResult)
        }
    }

    companion object {
        fun createPodDetails(pod: Pod, managementResult: ManagementResult): PodDetails {
            val status = pod.status.containerStatuses.firstOrNull()
            return PodDetails(
                    OpenShiftPodExcerpt(
                            name = pod.metadata.name,
                            status = pod.status.phase,
                            restartCount = status?.restartCount ?: 0,
                            ready = status?.ready ?: false,
                            podIP = pod.status.podIP,
                            deployment = pod.metadata.labels["deployment"],
                            startTime = pod.status.startTime
                    ),
                    managementResult
            )
        }
    }
}