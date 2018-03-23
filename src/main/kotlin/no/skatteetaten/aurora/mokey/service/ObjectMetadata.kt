package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig

val DeploymentConfig.imageStreamTag: String? get() = spec.triggers.find { it.type == "ImageChange" }
        ?.imageChangeParams?.from?.name?.split(":")?.lastOrNull()

val DeploymentConfig.managementPath: String?
    get() = metadata.annotations["console.skatteetaten.no/management-path"]

val DeploymentConfig.sprocketDone: String?
    get() = metadata.annotations["sprocket.sits.no-deployment-config.done"]

val DeploymentConfig.affiliation: String?
    get() = metadata.labels["affiliation"]

val DeploymentConfig.deployTag: String
    get() = metadata.labels["deployTag"] ?: ""

val DeploymentConfig.booberDeployId: String?
    get() = metadata.labels["booberDeployId"]

val ReplicationController.deploymentPhase: String?
    get() = metadata.annotations["openshift.io/deployment.phase"]