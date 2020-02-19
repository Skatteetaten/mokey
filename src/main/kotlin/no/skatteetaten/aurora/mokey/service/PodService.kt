package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newPod
import io.fabric8.kubernetes.api.model.Pod
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.OpenShiftContainerExcerpt
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.springframework.stereotype.Service

@Service
class PodService(
        @TargetClient(ClientTypes.SERVICE_ACCOUNT) val client: KubernetesCoroutinesClient,
        val managementDataService: ManagementDataService
) {

    fun getPodDetails(
            applicationDeployment: ApplicationDeployment,
            deployDetails: DeployDetails,
            selector: Map<String, String>
    ): List<PodDetails> {


        val pods = runBlocking {
            client.getMany(newPod {
                metadata {
                    namespace = applicationDeployment.metadata.namespace
                    labels = selector
                }
            })
        }
        return pods.map { pod: Pod ->
            //TODO: change this to use proxyGetEndpoint
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
