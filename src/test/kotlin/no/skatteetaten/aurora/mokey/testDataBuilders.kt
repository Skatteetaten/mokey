package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newContainerState
import com.fkorotkov.kubernetes.newContainerStatus
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newRawExtension
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.kubernetes.status
import com.fkorotkov.kubernetes.waiting
import com.fkorotkov.openshift.from
import com.fkorotkov.openshift.image
import com.fkorotkov.openshift.imageChangeParams
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newDeploymentTriggerPolicy
import com.fkorotkov.openshift.newImageStreamTag
import com.fkorotkov.openshift.newProject
import com.fkorotkov.openshift.newRoute
import com.fkorotkov.openshift.newRouteIngress
import com.fkorotkov.openshift.newRouteIngressCondition
import com.fkorotkov.openshift.spec
import com.fkorotkov.openshift.status
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.extensions.LABEL_AFFILIATION
import no.skatteetaten.aurora.mokey.extensions.LABEL_CREATED
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentSpec
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.utils.Right
import org.apache.commons.codec.digest.DigestUtils
import java.time.Instant

const val DEFAULT_NAME = "app-name"
const val DEFAULT_AFFILIATION = "affiliation"
const val DEFAULT_ENV_NAME = "namespace"

data class AuroraApplicationDeploymentDataBuilder(
    val appName: String = DEFAULT_NAME,
    val affiliation: String = DEFAULT_AFFILIATION,
    val envName: String = DEFAULT_ENV_NAME,
    val managementPath: String = ":8081/actuator",
    val deployTag: String = "name:tag",
    val selector: Map<String, String> = mapOf("name" to appName),
    val splunkIndex: String = "openshift-test",
    val releaseTo: String? = null,
    val exactGitRef: String = "abcd",
    val overrides: Map<String, String> = emptyMap(),
    val auroraConfigRefBranch: String = "master"
) {

    val appNamespace: String get() = "$affiliation-$envName"

    fun build(): ApplicationDeployment {
        return ApplicationDeployment(
            metadata = newObjectMeta {
                name = appName
                namespace = appNamespace
                labels = mapOf(
                    LABEL_AFFILIATION to affiliation
                )
            },
            spec = ApplicationDeploymentSpec(
                command = ApplicationDeploymentCommand(
                    overrideFiles = overrides,
                    auroraConfig = AuroraConfigRef(affiliation, exactGitRef),
                    applicationDeploymentRef = ApplicationDeploymentRef(
                        application = appName,
                        environment = appNamespace
                    )
                ),
                applicationId = DigestUtils.sha1Hex(appName),
                applicationName = appName,
                applicationDeploymentId = DigestUtils.sha1Hex(appName + appNamespace),
                applicationDeploymentName = appName,
                splunkIndex = splunkIndex,
                managementPath = managementPath,
                releaseTo = releaseTo,
                deployTag = deployTag,
                selector = selector
            )
        )
    }
}

data class DeploymentConfigDataBuilder(
    val dcName: String = DEFAULT_NAME,
    val dcAffiliation: String = DEFAULT_AFFILIATION,
    val dcEnvName: String = DEFAULT_ENV_NAME,
    val dcDeployTag: String = "name:tag",
    val dcSelector: Map<String, String> = mapOf("name" to dcName)
) {

    val dcNamespace: String get() = "$dcAffiliation-$dcEnvName"

    fun build(): DeploymentConfig {
        return newDeploymentConfig {

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

data class RouteBuilder(
    val routeName: String = DEFAULT_NAME,
    val routeHost: String = "affiliation-namespace-app-name",
    val routePath: String? = null,
    val statusDone: String = "True",
    val statusType: String = "Admitted",
    val statusReason: String? = null,
    val created: Instant = Instant.EPOCH,
    val routeAnnotations: Map<String, String> = mapOf()
) {

    fun build() =
        newRoute {
            metadata {
                name = routeName
                labels = mapOf(LABEL_CREATED to created.toString())
                annotations = routeAnnotations
            }
            spec {
                host = routeHost
                routePath?.let {
                    path = it
                }
            }
            status {
                ingress = listOf(
                    newRouteIngress {
                        conditions = listOf(
                            newRouteIngressCondition {
                                type = statusType
                                status = statusDone
                                statusReason?.let {
                                    reason = it
                                }
                            }
                        )
                    }
                )
            }
        }
}

data class ServiceBuilder(
    val serviceName: String = DEFAULT_NAME,
    val created: Instant = Instant.EPOCH,
    val serviceAnnotations: Map<String, String> = mapOf()
) {

    fun build() =
        newService {
            metadata {
                name = serviceName
                labels = mapOf(LABEL_CREATED to created.toString())
                annotations = serviceAnnotations
            }
        }
}

data class ReplicationControllerDataBuilder(val phase: String = "deploymentPhase") {

    fun build(): ReplicationController = newReplicationController { deploymentPhase = phase }
}

data class PodDataBuilder(
    val podName: String = "name",
    val ip: String = "127.0.0.1"
) {

    fun build() =
        newPod {
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
                        state = newContainerState {
                            waiting {
                                reason = "reason"
                            }
                        }
                    }
                )
            }
        }
}

data class ManagementDataBuilder(
    val info: InfoResponse = InfoResponse(),
    val health: HealthResponse = HealthResponse(HealthStatus.UP),
    val env: JsonNode = MissingNode.getInstance()
) {

    fun build() = Right(
        ManagementData(
            ManagementLinks(emptyMap()),
            Right(HttpResponse(info, "")),
            Right(HttpResponse(health, ""))
            /*, Right(env)*/
        )
    )
}

data class PodDetailsDataBuilder(
    val name: String = "name",
    val status: String = "status"
) {

    fun build() =
        PodDetails(
            OpenShiftPodExcerpt(
                name = name,
                status = status,
                deployment = "deployment",
                podIP = "127.0.0.1",
                startTime = ""
            ),
            ManagementDataBuilder().build()
        )
}

data class ImageDetailsDataBuilder(
    val dockerImageReference: String = "dockerImageReference",
    val environmentVariables: Map<String, String> = emptyMap()
) {

    fun build() = ImageDetails(dockerImageReference, Instant.now(), environmentVariables)
}

data class ProjectDataBuilder(val pName: String = "affiliation-name") {

    fun build() =
        newProject {
            metadata {
                name = pName
            }
        }
}

data class ImageStreamTagDataBuilder(
    val reference: String = "dockerImageReference",
    val env: Map<String, String> = mapOf()
) {

    fun build() =

        newImageStreamTag {
            image {
                dockerImageReference = reference
                dockerImageMetadata = newRawExtension {
                    val env = mapOf("Env" to env.map { "${it.key}=${it.value}" })
                    setAdditionalProperty("ContainerConfig", env)
                }
            }
        }
}

data class ApplicationDataBuilder(
    val applicationId: String = "abc123",
    val applicationDeploymentId: String = "cbd234",
    val name: String = DEFAULT_NAME,
    val envName: String = DEFAULT_ENV_NAME,
    val affiliation: String = DEFAULT_AFFILIATION
) {
    val namespace: String get() = "$affiliation-$envName"

    fun build(): ApplicationData =
        ApplicationData(
            applicationId,
            applicationDeploymentId,
            AuroraStatus(HEALTHY, "", listOf()),
            "",
            name,
            name,
            namespace,
            affiliation,
            deployDetails = DeployDetails(null, 1, 1),
            addresses = emptyList(),
            deploymentCommand = ApplicationDeploymentCommand(
                applicationDeploymentRef = ApplicationDeploymentRef(DEFAULT_ENV_NAME, "name"),
                auroraConfig = AuroraConfigRef(DEFAULT_AFFILIATION, "master")
            )
        )
}
