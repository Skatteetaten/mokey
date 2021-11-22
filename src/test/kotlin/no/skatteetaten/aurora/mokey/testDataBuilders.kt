package no.skatteetaten.aurora.mokey

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newContainer
import com.fkorotkov.kubernetes.newContainerState
import com.fkorotkov.kubernetes.newContainerStatus
import com.fkorotkov.kubernetes.newNamespace
import com.fkorotkov.kubernetes.newObjectMeta
import com.fkorotkov.kubernetes.newPod
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.kubernetes.newService
import com.fkorotkov.kubernetes.running
import com.fkorotkov.kubernetes.spec
import com.fkorotkov.kubernetes.status
import com.fkorotkov.kubernetes.template
import com.fkorotkov.kubernetes.terminated
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
import com.fkorotkov.openshift.runtime.newRawExtension
import com.fkorotkov.openshift.spec
import com.fkorotkov.openshift.status
import com.fkorotkov.openshift.tls
import io.fabric8.kubernetes.api.model.ContainerStatus
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentCommandResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentRefResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentResource
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentsWithDbResource
import no.skatteetaten.aurora.mokey.controller.ApplicationResource
import no.skatteetaten.aurora.mokey.controller.AuroraConfigRefResource
import no.skatteetaten.aurora.mokey.controller.AuroraStatusResource
import no.skatteetaten.aurora.mokey.controller.DeployDetailsResource
import no.skatteetaten.aurora.mokey.controller.GitInfoResource
import no.skatteetaten.aurora.mokey.controller.ImageDetailsResource
import no.skatteetaten.aurora.mokey.controller.Version
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_BOOBER_DEPLOYTAG
import no.skatteetaten.aurora.mokey.extensions.LABEL_CREATED
import no.skatteetaten.aurora.mokey.extensions.LABEL_DEPLOYTAG
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentSpec
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.DiscoveryResponse
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.OpenShiftContainerExcerpt
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.ServiceAddress
import no.skatteetaten.aurora.mokey.model.newApplicationDeployment
import no.skatteetaten.aurora.mokey.service.ImageBuildTimeline
import no.skatteetaten.aurora.mokey.service.ImageTagResource
import uk.q3c.rest.hal.Links
import java.net.URI
import java.time.Instant
import java.time.Instant.now

const val DEFAULT_NAME = "app-name"
const val DEFAULT_AFFILIATION = "affiliation"
const val DEFAULT_ENV_NAME = "namespace"

class ApplicationResourceBuilder {
    fun build() = ApplicationResource(
        id = "123",
        name = "name",
        applicationDeployments = listOf(ApplicationDeploymentResourceBuilder().build()),
    )
}

class ApplicationDeploymentResourceBuilder {
    fun build() = ApplicationDeploymentResource(
        id = "123",
        affiliation = "aurora",
        environment = "utv",
        namespace = "aurora-dev",
        name = "test",
        status = AuroraStatusResource(""),
        version = Version("123", "12345", null),
        dockerImageRepo = "",
        time = now(),
        message = null,
    )
}

class ApplicationDeploymentDetailsResourceBuilder {
    fun build() = ApplicationDeploymentDetailsResource(
        id = "123",
        updatedBy = "user",
        buildTime = now(),
        gitInfo = GitInfoResource(),
        imageDetails = ImageDetailsResource(null, null, null),
        podResources = emptyList(),
        databases = emptyList(),
        applicationDeploymentCommand = ApplicationDeploymentCommandResource(
            applicationDeploymentRef = ApplicationDeploymentRefResource(
                environment = "utv",
                application = "test-app",
            ),
            auroraConfig = AuroraConfigRefResource("test-config", "ref"),
        ),
        deployDetails = DeployDetailsResource(
            targetReplicas = 1,
            availableReplicas = 2,
            paused = false,
        ),
        serviceLinks = Links(),
    )
}

class ApplicationDeploymentsWithDbResourceBuilder {
    fun build() = ApplicationDeploymentsWithDbResource(
        databaseId = "123",
        applicationDeployments = emptyList(),
    )
}

data class DeploymentConfigDataBuilder(
    val dcName: String = DEFAULT_NAME,
    val dcAffiliation: String = DEFAULT_AFFILIATION,
    val dcEnvName: String = DEFAULT_ENV_NAME,
    val dcDeployTag: String = "name:tag",
    val dcSelector: Map<String, String> = mapOf("name" to dcName),
    val dcLatestVersion: Long = 1,
    val pause: Boolean = false,
) {
    val dcNamespace: String get() = "$dcAffiliation-$dcEnvName"

    fun build(): DeploymentConfig = newDeploymentConfig {
        metadata {
            name = dcName
            namespace = dcNamespace
            labels = mapOf("updatedBy" to "linus")
        }
        status {
            latestVersion = dcLatestVersion
        }
        spec {
            if (pause) {
                paused = true
            }
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

data class RouteBuilder(
    val routeName: String = DEFAULT_NAME,
    val routeHost: String = "affiliation-namespace-app-name",
    val routePath: String? = null,
    val tlsEnabled: Boolean = false,
    val statusDone: String = "True",
    val statusType: String = "Admitted",
    val statusReason: String? = null,
    val created: Instant = Instant.EPOCH,
    val routeAnnotations: Map<String, String> = mapOf(),
) {
    fun build() = newRoute {
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
            if (tlsEnabled) {
                tls {
                    termination = "edge"
                }
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
    val serviceAnnotations: Map<String, String> = mapOf(),
) {
    fun build() = newService {
        metadata {
            name = serviceName
            labels = mapOf(LABEL_CREATED to created.toString())
            annotations = serviceAnnotations
        }
    }
}

class ImageTagResourceBuilder {
    fun build() = ImageTagResource(
        auroraVersion = "4.0.0-b1.23.1-wingnut11-1.3.3",
        timeline = ImageBuildTimeline(now(), now()),
        dockerDigest = "sha256:123hash",
        requestUrl = "docker-registry/group/name/sha256:123hash",
        dockerVersion = "1.13.1",
    )
}

data class ReplicationControllerDataBuilder(
    val rcPhase: String = "deploymentPhase",
    val rcDeployTag: String = "4.0.5",
    val rcSpecReplicas: Int = 1,
    val rcAvailableReplicas: Int = 1,
    val rcStatusReplicas: Int = 0,
    val rcContainers: Map<String, String> = mapOf("name-java" to "docker-registry/group/name@sha256:123hash"),
) {
    fun build(): ReplicationController = newReplicationController {
        metadata = newObjectMeta {
            labels = mapOf(LABEL_DEPLOYTAG to rcDeployTag)
        }
        deploymentPhase = rcPhase

        spec {
            replicas = rcSpecReplicas

            template {
                metadata {
                    annotations = mapOf(
                        ANNOTATION_BOOBER_DEPLOYTAG to rcDeployTag
                    )
                }
                spec {
                    containers = rcContainers.map {
                        newContainer {
                            name = it.key
                            image = it.value
                        }
                    }
                }
            }
        }

        status {
            availableReplicas = rcAvailableReplicas
            replicas = rcStatusReplicas
        }
    }
}

enum class ContainerStatuses {
    RUNNING,
    WAITING,
    TERMINATED
}

data class ContainerStatusBuilder(
    val containerName: String = "name-java",
    val containerRestart: Int = 1,
    val contaienerReady: Boolean = true,
    val containerStatus: ContainerStatuses = ContainerStatuses.RUNNING,
    val containerImageID: String = "docker-pullable://docker-registry.aurora.sits.no:5000/" +
        "something@sha256:0c7e422b9d6c7be89a676e8f670c9292862cc06fc8b2fd656d2f6025dee411dc",
) {
    fun build() = newContainerStatus {
        restartCount = containerRestart
        ready = contaienerReady
        name = containerName
        imageID = containerImageID
        state = newContainerState {
            when (containerStatus) {
                ContainerStatuses.RUNNING -> running {
                    startedAt = "now"
                }
                ContainerStatuses.WAITING -> waiting { reason = "waiting" }
                ContainerStatuses.TERMINATED -> terminated { reason = "terminated" }
            }
        }
    }
}

data class PodDataBuilder(
    val podName: String = "name",
    val ip: String = "127.0.0.1",
    val containerList: List<ContainerStatus> = listOf(ContainerStatusBuilder().build()),
) {
    fun build() = newPod {
        metadata {
            name = podName
            namespace = "namespace"
            labels = mapOf("replicaName" to "replicaName")
        }
        spec {
            containers = containerList.map {
                newContainer {
                    name = it.name
                }
            }
        }
        status {
            podIP = ip
            startTime = ""
            phase = "phase"
            containerStatuses = containerList
        }
    }
}

data class PodDetailsDataBuilder(
    val name: String = "name",
    val status: String = "phase",
    val deployment: String = "replicaName",
    val startTime: Instant? = Instant.EPOCH,
    val deployTag: String = "1",
    val managementDataBuilder: ManagementDataBuilder = ManagementDataBuilder(),
    val containers: List<OpenShiftContainerExcerpt> = emptyList(),
    val latestDeployTag: Boolean = true,
    val latestDeployment: Boolean = true,
    val startTimeString: String? = null,
) {
    fun build(): PodDetails = PodDetails(
        OpenShiftPodExcerpt(
            name = name,
            phase = status,
            podIP = "127.0.0.1",
            startTime = startTime?.toString() ?: startTimeString,
            replicaName = deployment,
            deployTag = deployTag,
            containers = containers,
            latestDeployTag = latestDeployTag,
            latestReplicaName = latestDeployment,
        ),
        managementDataBuilder.build(),
    )
}

data class ImageDetailsDataBuilder(
    val dockerImageReference: String = "dockerImageReference",
    val dockerImageTagReference: String = "dockerImageTagReference",
    val environmentVariables: Map<String, String> = emptyMap(),
) {
    fun build() = ImageDetails(dockerImageReference, dockerImageTagReference, now(), environmentVariables)
}

data class ProjectDataBuilder(val pName: String = "affiliation-name") {
    fun build() = newProject {
        metadata {
            name = pName
            namespace = "namespace"
        }
    }
}

data class NamespaceDataBuilder(val pName: String = "affiliation-name") {
    fun build() = newNamespace {
        metadata {
            name = pName
            namespace = "namespace"
        }
    }
}

data class ImageStreamTagDataBuilder(
    val reference: String = "dockerImageReference",
    val env: Map<String, String> = mapOf(),
) {
    fun build() = newImageStreamTag {
        image {
            dockerImageReference = reference
            dockerImageMetadata = newRawExtension {
                val env = mapOf("Env" to env.map { "${it.key}=${it.value}" })
                setAdditionalProperty("Config", env)
            }
        }
    }
}

data class ApplicationDeploymentBuilder(val runnableType: String? = null) {
    fun build(): ApplicationDeployment = newApplicationDeployment {
        metadata {
            name = "mokey"
            labels = mapOf("affiliation" to "aurora")
            namespace = "aurora-dev"
            annotations = emptyMap()
        }
        spec = ApplicationDeploymentSpec(
            command = ApplicationDeploymentCommand(
                overrideFiles = emptyMap(),
                auroraConfig = AuroraConfigRef("aurora", "abc"),
                applicationDeploymentRef = ApplicationDeploymentRef(
                    application = "mokey",
                    environment = "aurora-dev"
                )
            ),
            runnableType = runnableType,
            applicationId = "123",
            applicationName = "mokey",
            applicationDeploymentId = "234",
            applicationDeploymentName = "test-mokey",
            databases = emptyList(),
            splunkIndex = null,
            managementPath = ":8081/management",
            releaseTo = null,
            deployTag = null,
            selector = emptyMap(),
            message = null
        )
    }
}

class AddressBuilder {
    fun build() = ServiceAddress(url = URI("/mokey"), time = null)
}

class AuroraStatusBuilder {
    fun build() = AuroraStatus(level = HEALTHY)
}

data class ApplicationDataBuilder(
    val applicationId: String = "abc123",
    val applicationDeploymentId: String = "cbd234",
    val name: String = DEFAULT_NAME,
    val envName: String = DEFAULT_ENV_NAME,
    val affiliation: String = DEFAULT_AFFILIATION
) {
    val namespace: String get() = "$affiliation-$envName"

    fun build(): ApplicationData = ApplicationData(
        deployDetails = DeployDetails(
            availableReplicas = 1,
            targetReplicas = 1,
            phase = "Complete",
            deployTag = "1"
        ),
        addresses = emptyList(),
        databases = listOf("123"),
        deploymentCommand = ApplicationDeploymentCommand(
            applicationDeploymentRef = ApplicationDeploymentRef(DEFAULT_ENV_NAME, "name"),
            auroraConfig = AuroraConfigRef(DEFAULT_AFFILIATION, "master")
        ),
        pods = listOf(
            PodDetails(
                openShiftPodExcerpt = OpenShiftPodExcerpt(
                    name = "name",
                    phase = "phase",
                    podIP = null,
                    startTime = null,
                    replicaName = null,
                    deployTag = null,
                    containers = emptyList(),
                    latestDeployTag = true,
                    latestReplicaName = true
                ),
                managementData = ManagementData(
                    info =
                    ManagementEndpointResult(
                        endpointType = EndpointType.INFO,
                        resultCode = "",
                        deserialized = InfoResponse(
                            podLinks = mapOf(
                                "test" to "http://localhost",
                                "metrics" to "{metricsHostname}"
                            )
                        )
                    ),
                    links = ManagementEndpointResult(
                        endpointType = EndpointType.INFO,
                        resultCode = "",
                        deserialized = DiscoveryResponse(emptyMap())
                    )
                )
            )
        ),
        publicData = ApplicationPublicData(
            applicationId = applicationId,
            applicationDeploymentId = applicationDeploymentId,
            applicationName = name,
            applicationDeploymentName = name,
            auroraStatus = AuroraStatus(HEALTHY),
            affiliation = affiliation,
            namespace = namespace,
            deployTag = "",
            dockerImageRepo = null,
            releaseTo = "releaseTo",
            time = Instant.EPOCH,
            environment = envName
        )
    )
}
