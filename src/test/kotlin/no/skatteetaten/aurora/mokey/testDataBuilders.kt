package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fkorotkov.kubernetes.*
import com.fkorotkov.openshift.*
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Project
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.service.*
import no.skatteetaten.aurora.utils.Right

data class DeploymentConfigDataBuilder(
        val dcName: String = "app-name",
        val dcNamespace: String = "namespace",
        val dcAffiliation: String = "affiliation",
        val dcManagementPath: String = "/management-path",
        val dcDeployTag: String = "name:tag",
        val dcSelector: Map<String, String> = mapOf("name" to dcName)) {

    fun build(): DeploymentConfig {
        return newDeploymentConfig {
            managementPath = dcManagementPath
            affiliation = dcAffiliation

            metadata {
                name = dcName
                namespace = dcNamespace
            }
            status {
                latestVersion = 1
            }
            spec {
                replicas = 1
                selector = dcSelector
                triggers = listOf(
                        newDeploymentTriggerPolicy {
                            type = "ImageChange"
                            imageChangeParams {
                                from {
                                    name = dcDeployTag
                                }
                            }
                        }
                )
            }
        }
    }
}

data class ReplicationControllerDataBuilder(private val phase: String = "deploymentPhase") {

    fun build(): ReplicationController = newReplicationController { deploymentPhase = phase }
}

data class PodDataBuilder(
        val podName: String = "name",
        val ip: String = "127.0.0.1") {

    fun build(): Pod {
        return newPod {
            metadata {
                name = podName
                labels = mapOf("deployment" to "deployment")
            }
            status {
                podIP = ip
                startTime = ""
                phase = "phase"
                containerStatuses = listOf(
                        newContainerStatus {
                            restartCount = 1
                            ready = true
                        }
                )
            }
        }
    }
}

data class ManagementDataBuilder(
        val info: JsonNode = MissingNode.getInstance(),
        val health: JsonNode = MissingNode.getInstance(),
        val env: JsonNode = MissingNode.getInstance()
) {

    fun build(): ManagementResult {
        return Right(ManagementData(ManagementLinks(emptyMap()), Right(info), Right(health), Right(env)))
    }
}

data class PodDetailsDataBuilder(
        private val name: String = "name",
        private val status: String = "status") {

    fun build(): PodDetails {
        return PodDetails(
                OpenShiftPodExcerpt(name = name, status = status, deployment = "deployment", podIP = "127.0.0.1", startTime = ""),
                ManagementDataBuilder().build())
    }
}

data class ImageDetailsDataBuilder(private val dockerImageReference: String = "dockerImageReference",
                                   private val environmentVariables: Map<String, String> = emptyMap()) {

    fun build(): ImageDetails {
        return ImageDetails(dockerImageReference, environmentVariables)
    }
}

data class ProjectDataBuilder(private val pName: String = "affiliation-name") {

    fun build(): Project {
        return newProject {
            metadata {
                name = pName
            }
        }
    }
}

data class ImageStreamTagDataBuilder(private val dockerImageReference: String = "dockerImageReference") {

    fun build(): ImageStreamTag {
        return ImageStreamTag(
                image = Image(dockerImageMetadata = Metadata(containerConfig = ContainerConfig(emptyList())),
                        dockerImageReference = dockerImageReference))
    }
}
