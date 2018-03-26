package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig

const val ANNOTATION_PHASE = "openshift.io/deployment.phase"

const val ANNOTATION_MANAGEMENT_PATH = "console.skatteetaten.no/management-path"
const val ANNOTATION_SPROCKET_DONE = "sprocket.sits.no-deployment-config.done"
const val LABEL_AFFILIATION = "affiliation"
const val LABEL_DEPLOYTAG = "deployTag"
const val LABEL_BOOBER_DEPLOY_ID = "booberDeployId"

val DeploymentConfig.imageStreamTag: String?
    get() = spec.triggers.find { it.type == "ImageChange" }
            ?.imageChangeParams?.from?.name?.split(":")?.lastOrNull()

var DeploymentConfig.managementPath: String?
    get() = safeMetadataAnnotations()[ANNOTATION_MANAGEMENT_PATH]
    set(value) = safeMetadataAnnotations().set(ANNOTATION_MANAGEMENT_PATH, value)

val DeploymentConfig.sprocketDone: String?
    get() = safeMetadataAnnotations()[ANNOTATION_SPROCKET_DONE]

var DeploymentConfig.affiliation: String?
    get() = safeMetadataLabels()[LABEL_AFFILIATION]
    set(value) = safeMetadataLabels().set(LABEL_AFFILIATION, value)

val DeploymentConfig.deployTag: String
    get() = safeMetadataLabels()[LABEL_DEPLOYTAG] ?: ""

val DeploymentConfig.booberDeployId: String?
    get() = safeMetadataLabels()[LABEL_BOOBER_DEPLOY_ID]

var ReplicationController.deploymentPhase: String?
    get() = safeMetadataAnnotations()[ANNOTATION_PHASE]
    set(value) = safeMetadataAnnotations().set(ANNOTATION_PHASE, value)

private fun HasMetadata.safeMetadataAnnotations(): MutableMap<String, String?> {
    if (safeMetadata().annotations == null) metadata.annotations = mutableMapOf<String, String>()
    return metadata.annotations
}

private fun HasMetadata.safeMetadataLabels(): MutableMap<String, String?> {
    if (safeMetadata().labels == null) metadata.labels = mutableMapOf<String, String>()
    return metadata.labels
}

private fun HasMetadata.safeMetadata(): ObjectMeta {
    if (metadata == null) metadata = ObjectMeta()
    return metadata
}
