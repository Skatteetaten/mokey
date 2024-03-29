package no.skatteetaten.aurora.mokey.extensions

import io.fabric8.kubernetes.api.model.HasMetadata
import io.fabric8.kubernetes.api.model.ObjectMeta
import io.fabric8.kubernetes.api.model.PodTemplateSpec
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Route
import java.time.Instant
import java.time.format.DateTimeFormatter

const val ANNOTATION_PHASE = "openshift.io/deployment.phase"
const val LABEL_AFFILIATION = "affiliation"
const val LABEL_CREATED = "creationTimestamp"
const val LABEL_DEPLOYTAG = "deployTag"
const val LABEL_UPDATED_BY = "updatedBy"
const val LABEL_BOOBER_DEPLOY_ID = "booberDeployId"
const val ANNOTATION_MARJORY_SERVICE = "sprocket.sits.no/service.webseal"
const val ANNOTATION_MARJORY_DONE = "marjory.sits.no-routes-config.done"
const val ANNOTATION_MARJORY_OPEN = "marjory.sits.no/isOpen"
const val ANNOTATION_MARJORY_ROLES = "marjory.sits.no/route.roles"
const val ANNOTATION_WEMBLEY_SERVICE = "wembley.sits.no/serviceName"
const val ANNOTATION_WEMBLEY_DONE = "wembley.sits.no/done"
const val ANNOTATION_WEMBLEY_PATHS = "wembley.sits.no/apiPaths"
const val ANNOTATION_WEMBLEY_EXTERNAL_HOST = "wembley.sits.no/externalHost"
@Suppress("unused")
const val ANNOTATION_WEMBLEY_ASM = "wembley.sits.no/asmPolicy"
const val ANNOTATION_BOOBER_DEPLOYTAG = "boober.skatteetaten.no/deployTag"

val DeploymentConfig.imageStreamNameAndTag: Pair<String, String>?
    get() = spec.triggers.find { it.type == "ImageChange" }?.imageChangeParams?.from?.name?.split(":")?.let {
        when {
            it.size != 2 -> null
            else -> it[0] to it[1]
        }
    }

val Route.marjoryDone: Instant?
    get() = safeMetadataAnnotations()[ANNOTATION_MARJORY_DONE]
        ?.let { DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(it, Instant::from) }

val Route.marjoryOpen: Boolean
    get() = safeMetadataAnnotations()[ANNOTATION_MARJORY_OPEN]?.let { it == "true" } ?: false

val Route.marjoryRoles: List<String>
    get() = safeMetadataAnnotations()[ANNOTATION_MARJORY_ROLES]?.split(",") ?: listOf()

val Route.wembleyService: String?
    get() = safeMetadataAnnotations()[ANNOTATION_WEMBLEY_SERVICE]

val Route.wembleyHost: String?
    get() = safeMetadataAnnotations()[ANNOTATION_WEMBLEY_EXTERNAL_HOST]

val Service.marjoryDone: String?
    get() = safeMetadataAnnotations()[ANNOTATION_MARJORY_DONE]

val HasMetadata.created: Instant?
    get() = safeMetadataLabels()[LABEL_CREATED]?.let(Instant::parse)

var ObjectMeta.affiliation: String?
    get() = safeMetadataLabels()[LABEL_AFFILIATION]
    set(value) = safeMetadataLabels().set(LABEL_AFFILIATION, value)

val DeploymentConfig.deployTag: String
    get() = safeMetadataLabels()[LABEL_DEPLOYTAG] ?: ""

val ReplicationController.deployTag: String
    get() = this.spec.template.safeMetadata().safeMetadataAnnotations()[ANNOTATION_BOOBER_DEPLOYTAG]
        ?: safeMetadataLabels()[LABEL_DEPLOYTAG] ?: ""

val ReplicaSet.deployTag: String
    get() = this.spec.template.safeMetadata().safeMetadataAnnotations()[ANNOTATION_BOOBER_DEPLOYTAG]
        ?: safeMetadataLabels()[LABEL_DEPLOYTAG] ?: ""

val ObjectMeta.booberDeployId: String?
    get() = safeMetadataLabels()[LABEL_BOOBER_DEPLOY_ID]

val DeploymentConfig.updatedBy: String?
    get() = safeMetadataLabels()[LABEL_UPDATED_BY]

val Deployment.updatedBy: String?
    get() = safeMetadataLabels()[LABEL_UPDATED_BY]

val ReplicaSet.revision: String?
    get() = safeMetadataAnnotations()["deployment.kubernetes.io/revision"]

var ReplicationController.deploymentPhase: String?
    get() = safeMetadataAnnotations()[ANNOTATION_PHASE]
    set(value) = safeMetadataAnnotations().set(ANNOTATION_PHASE, value)

private fun ObjectMeta.safeMetadataAnnotations(): MutableMap<String, String?> {
    if (this.annotations == null) this.annotations = mutableMapOf<String, String>()

    return this.annotations
}

private fun HasMetadata.safeMetadataAnnotations(): MutableMap<String, String?> {
    if (safeMetadata().annotations == null) metadata.annotations = mutableMapOf<String, String>()

    return metadata.annotations
}

private fun ObjectMeta.safeMetadataLabels(): MutableMap<String, String?> {
    if (this.labels == null) this.labels = mutableMapOf<String, String>()

    return this.labels
}

private fun HasMetadata.safeMetadataLabels(): MutableMap<String, String?> {
    if (safeMetadata().labels == null) metadata.labels = mutableMapOf<String, String>()

    return metadata.labels
}

private fun HasMetadata.safeMetadata(): ObjectMeta {
    if (metadata == null) metadata = ObjectMeta()

    return metadata
}

private fun PodTemplateSpec.safeMetadata(): ObjectMeta {
    if (metadata == null) metadata = ObjectMeta()

    return metadata
}
