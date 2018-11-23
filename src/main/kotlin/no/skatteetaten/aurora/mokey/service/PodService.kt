package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.OpenShiftContainerExcerpt
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
            val managementResult =
                managementDataService.load(pod.status.podIP, applicationDeployment.spec.managementPath)
            createPodDetails(pod, managementResult)
        }
    }

    companion object {
        fun createPodDetails(pod: Pod, managementResult: ManagementData): PodDetails {
            val containers = pod.spec.containers.mapNotNull { container ->
                crateContainerExcerpt(pod, container.name)
            }

            return PodDetails(
                OpenShiftPodExcerpt(
                    name = pod.metadata.name,
                    phase = pod.status.phase,
                    podIP = pod.status.podIP,
                    deployment = pod.metadata.labels["deployment"],
                    startTime = pod.status.startTime,
                    containers = containers
                ),
                managementResult
            )
        }

        private fun crateContainerExcerpt(
            pod: Pod,
            containerName: String
        ): OpenShiftContainerExcerpt? {
            val status = pod.status.containerStatuses.firstOrNull { it.name == containerName } ?: return null

            val state = status.state.terminated?.let {
                "terminated"
            } ?: status.state.waiting?.let {
                "waiting"
            } ?: "running"

            return OpenShiftContainerExcerpt(
                name = containerName,
                image = status.imageID.substringAfterLast("docker-pullable://"),
                ready = status.ready,
                restartCount = status.restartCount,
                state = state
            )
        }
    }
}