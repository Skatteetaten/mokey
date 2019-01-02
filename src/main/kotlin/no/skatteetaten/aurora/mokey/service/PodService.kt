package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.Pod
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.DeployDetails
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

    fun getPodDetails(
        applicationDeployment: ApplicationDeployment,
        deployDetails: DeployDetails,
        selector: Map<String, String> = applicationDeployment.spec.selector
    ): List<PodDetails> {

        val pods = openShiftService.pods(applicationDeployment.metadata.namespace, selector)
        return pods.map { pod: Pod ->
            val managementResult =
                managementDataService.load(pod.status.podIP, applicationDeployment.spec.managementPath)
            createPodDetails(pod, managementResult, deployDetails)
        }
    }

    companion object {
        fun createPodDetails(
            pod: Pod,
            managementResult: ManagementData,
            deployDetails: DeployDetails
        ): PodDetails {
            val containers = pod.spec.containers.mapNotNull { container ->
                createContainerExcerpt(pod, container.name)
            }

            val podDeployment = pod.metadata.labels["deployment"]
            val deployTag = pod.metadata.labels["deployTag"]

            val latestDeployment = podDeployment == deployDetails.deployment
            val latestDeployTag = deployTag == deployDetails.deployTag
            return PodDetails(
                OpenShiftPodExcerpt(
                    name = pod.metadata.name,
                    phase = pod.status.phase,
                    podIP = pod.status.podIP,
                    replicaName = podDeployment,
                    startTime = pod.status.startTime,
                    deployTag = deployTag,
                    latestDeployTag = latestDeployTag,
                    latestReplicaName = latestDeployment,
                    containers = containers
                ),
                managementResult
            )
        }

        private fun createContainerExcerpt(
            pod: Pod,
            containerName: String
        ): OpenShiftContainerExcerpt? {
            val status = pod.status.containerStatuses.firstOrNull { it.name == containerName } ?: return null

            val state = status.state.terminated?.let {
                "terminated"
            } ?: status.state.waiting?.let {
                "waiting"
            } ?: "running"

            val image = status.imageID.substringAfterLast("docker-pullable://")
            return OpenShiftContainerExcerpt(
                name = containerName,
                image = image,
                ready = status.ready,
                restartCount = status.restartCount,
                state = state
            )
        }
    }
}