package no.skatteetaten.aurora.mokey

import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newProject
import com.fkorotkov.openshift.spec
import com.fkorotkov.openshift.status
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Project
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails

data class DeploymentConfigDataBuilder(
        private val dcName: String = "name",
        private val dcNamespace: String = "namespace",
        private val dcAffiliation: String = "affiliation",
        private val dcLatestVersion: Long = 1,
        private val dcReplicas: Int = 1) {

    fun build(): DeploymentConfig {
        return newDeploymentConfig {
            metadata {
                name = dcName
                namespace = dcNamespace
                labels = mapOf("affiliation" to dcAffiliation)
            }
            status {
                latestVersion = dcLatestVersion
            }
            spec {
                replicas = dcReplicas
            }
        }
    }
}

data class PodDetailsDataBuilder(
        private val name: String = "name",
        private val status: String = "status",
        private val deployment: String = "deployment",
        private val podIP: String = "",
        private val startTime: String = "") {

    fun build(): PodDetails {
        return PodDetails(
                OpenShiftPodExcerpt(name = name, status = status, deployment = deployment, podIP = podIP, startTime = startTime),
                ManagementData())
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
