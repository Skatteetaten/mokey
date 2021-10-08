package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.CancellationException
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deployTag
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.imageStreamNameAndTag
import no.skatteetaten.aurora.mokey.extensions.revision
import no.skatteetaten.aurora.mokey.extensions.updatedBy
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.DeploymentResult
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.pmapIO
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.time.Duration.ofMinutes
import java.time.Instant
import java.time.Instant.now
import java.util.Locale.getDefault

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataServiceOpenShift(
    val client: OpenShiftServiceAccountClient,
    val auroraStatusCalculator: AuroraStatusCalculator,
    val podService: PodService,
    val addressService: AddressService,
    val imageService: ImageService,
    @Value("\${integrations.openshift:true}") val isOpenShift: Boolean,
) {
    suspend fun findAndGroupAffiliations(
        affiliations: List<String> = emptyList(),
    ): Map<String, List<Environment>> = findAllEnvironments().filter {
        if (affiliations.isNotEmpty()) {
            affiliations.contains(it.affiliation)
        } else true
    }.groupBy { it.affiliation }

    private suspend fun findAllEnvironments(): List<Environment> = client.getAllNamespace().map {
        Environment.fromNamespace(it.metadata.name)
    }

    suspend fun findAllApplicationDataByEnvironments(
        applicationDeployments: List<ApplicationDeployment>,
    ): List<ApplicationData> {
        val results = applicationDeployments.pmapIO { tryCreateApplicationData(it) }
        val errors = results.mapNotNull { it.error }
        val data = results.mapNotNull { it.applicationData }

        return data.also {
            logger.debug(
                "Found deployments=${applicationDeployments.size} " +
                    "data=${it.size} " +
                    "result=${results.size} " +
                    "errors=${errors.size}"
            )
        }
    }

    suspend fun findAllApplicationDeployments(environments: List<Environment>): List<ApplicationDeployment> {
        logger.debug("finding all applications in environments=$environments")

        return environments.flatMap { environment ->
            logger.debug("Finding ApplicationDeployments in namespace={}", environment)

            client.getApplicationDeployments(environment.namespace)
        }
    }

    suspend fun createSingleItem(namespace: String, name: String): ApplicationData {
        val applicationDeployment = client.getApplicationDeployment(name, namespace)

        return tryCreateApplicationData(applicationDeployment).applicationData
            ?: throw tryCreateApplicationData(applicationDeployment).error!!
    }

    private data class MaybeApplicationData(
        val applicationDeployment: ApplicationDeployment,
        val applicationData: ApplicationData? = null,
        val error: Exception? = null,
    )

    private suspend fun tryCreateApplicationData(it: ApplicationDeployment): MaybeApplicationData = runCatching {
        MaybeApplicationData(
            applicationDeployment = it,
            applicationData = createApplicationData(it)
        )
    }.recoverCatching { e ->
        when (e) {
            is WebClientResponseException.TooManyRequests -> throw e
            is InterruptedException,
            is CancellationException -> logger.info(
                "Interrupted getting deployment " +
                    "name=${it.metadata.name}, " +
                    "namespace=${it.metadata.namespace}"
            )
            else -> {
                val cause = e.cause?.let { it::class.simpleName }
                val rootCause = ExceptionUtils.getRootCause(e)?.let { it::class.simpleName }

                logger.info(
                    "Failed getting deployment " +
                        "name=${it.metadata.name}, " +
                        "namespace=${it.metadata.namespace} " +
                        "message=${e.message} " +
                        "cause=$cause " +
                        "rootCause=$rootCause",
                    e
                )
            }
        }

        MaybeApplicationData(applicationDeployment = it, error = e as Exception)
    }.getOrThrow()

    private fun applicationPublicData(
        applicationDeployment: ApplicationDeployment,
        auroraStatus: AuroraStatus,
    ): ApplicationPublicData {
        val affiliation = applicationDeployment.metadata.affiliation
        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name
        val applicationDeploymentName = applicationDeployment.spec.applicationDeploymentName
            ?: throw OpenShiftObjectException(
                "applicationDeploymentName was not set for deployment $namespace/$openShiftName"
            )
        val applicationName = applicationDeployment.spec.applicationName ?: throw OpenShiftObjectException(
            "applicationName was not set for deployment $namespace/$openShiftName"
        )

        return ApplicationPublicData(
            applicationId = applicationDeployment.spec.applicationId,
            applicationDeploymentId = applicationDeployment.spec.applicationDeploymentId,
            auroraStatus = auroraStatus,
            applicationName = applicationName,
            applicationDeploymentName = applicationDeploymentName,
            namespace = namespace,
            affiliation = affiliation,
            deployTag = applicationDeployment.spec.deployTag ?: "",
            releaseTo = applicationDeployment.spec.releaseTo,
            message = applicationDeployment.spec.message,
            environment = applicationDeployment.spec.command.applicationDeploymentRef.environment,
        )
    }

    private fun applicationData(
        applicationDeployment: ApplicationDeployment,
        applicationPublicData: ApplicationPublicData,
    ): ApplicationData {
        val databases = applicationDeployment.spec.databases ?: listOf()

        return ApplicationData(
            booberDeployId = applicationDeployment.metadata.booberDeployId,
            managementPath = applicationDeployment.spec.managementPath,
            deploymentCommand = applicationDeployment.spec.command,
            databases = databases,
            publicData = applicationPublicData,
        )
    }

    private suspend fun createApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
        logger.debug(
            "creating application data for " +
                "deployment=${applicationDeployment.metadata.name} " +
                "namespace ${applicationDeployment.metadata.namespace}"
        )

        val runnableType = applicationDeployment.spec.runnableType ?: "DeploymentConfig"

        if (runnableType == "Job" || runnableType == "CronJob") return createDisabledApplication(applicationDeployment)

        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name
        val result = if (runnableType == "Deployment") {
            val deployment = client.getDeployment(
                namespace,
                openShiftName
            ) ?: return createDisabledApplication(
                applicationDeployment
            )

            findDeployResultForDeployment(deployment, namespace)
        } else {
            val dc = client.getDeploymentConfig(
                namespace,
                openShiftName
            ) ?: return createDisabledApplication(applicationDeployment)

            findDeployResultForDC(namespace, dc)
        }

        val deployDetails = result.details
        val imageDetails = result.image
        val pods = podService.getPodDetails(applicationDeployment, deployDetails, result.selector)
        val applicationAddresses = when {
            isOpenShift -> addressService.getAddresses(namespace, openShiftName)
            else -> addressService.getIngressAddresses(namespace, openShiftName)
        }
        val auroraStatus = auroraStatusCalculator.calculateAuroraStatus(deployDetails, pods)
        val splunkIndex = applicationDeployment.spec.splunkIndex
        val deployTag = deployDetails.deployTag.takeIf { !it.isNullOrEmpty() }
            ?: (applicationDeployment.spec.deployTag ?: "")
        val apd = applicationPublicData(
            applicationDeployment,
            auroraStatus
        ).copy(
            auroraVersion = imageDetails?.auroraVersion,
            dockerImageRepo = imageDetails?.dockerImageRepo,
            deployTag = deployTag,
        )

        logger.debug(
            "/creating application data for " +
                "deployment=${applicationDeployment.metadata.name} " +
                "namespace ${applicationDeployment.metadata.namespace}"
        )

        return applicationData(applicationDeployment, apd).copy(
            pods = pods,
            imageDetails = imageDetails,
            addresses = applicationAddresses,
            splunkIndex = splunkIndex,
            deployDetails = deployDetails,
        )
    }

    @Suppress("DuplicatedCode")
    private suspend fun findDeployResultForDC(
        namespace: String,
        dc: DeploymentConfig,
    ): DeploymentResult {
        val replicationControllers = client.getReplicationControllers(
            namespace,
            mapOf("app" to dc.metadata.name)
        ).sortedByDescending {
            it.metadata.name.substringAfterLast("-").toInt()
        }
        val latestRc = replicationControllers.firstOrNull()
        val runningRc = replicationControllers.firstOrNull {
            it.isRunning()
        }
        val deployDetails = createDeployDetails(dc, runningRc)
        val imageDetails: ImageDetails? = when (runningRc) {
            latestRc, null -> {
                dc.imageStreamNameAndTag?.let {
                    imageService.getImageDetailsFromImageStream(dc.metadata.namespace, it.first, it.second)
                }
            }
            else -> {
                val image = runningRc.spec.template.spec.containers[0].image

                when {
                    image.substring(0, 2).toIntOrNull() != null -> ImageDetails(image)
                    else -> runCatching {
                        imageService.getCachedOrFind(image)
                    }.onFailure {
                        logger.warn(
                            "Failed getting imageDetails for " +
                                "namespace=${dc.metadata.namespace} " +
                                "name=${dc.metadata.name} " +
                                "image=$image",
                            it
                        )
                    }.getOrNull()
                }
            }
        }

        return DeploymentResult(
            deployDetails,
            imageDetails,
            dc.spec.selector,
        )
    }

    private fun createDeployDetails(
        dc: DeploymentConfig,
        runningRc: ReplicationController?,
    ): DeployDetails {
        val phase = dc.status.conditions.findOpenshiftPhase(ofMinutes(1L), now())

        return DeployDetails(
            targetReplicas = dc.spec.replicas,
            availableReplicas = dc.status.availableReplicas ?: 0,
            deployment = runningRc?.metadata?.name,
            deployTag = runningRc?.deployTag,
            paused = dc.spec.paused ?: false,
            phase = phase,
            updatedBy = dc.updatedBy,
            scaledDown = dc.metadata.labels["scale-down"],
        )
    }

    fun createDeployDetails(
        deployment: Deployment,
        runningRc: ReplicaSet?,
    ): DeployDetails {
        val phase = deployment.status.conditions.findPhase(ofMinutes(1L), now())

        return DeployDetails(
            targetReplicas = deployment.spec.replicas,
            availableReplicas = deployment.status.availableReplicas ?: 0,
            deployment = runningRc?.metadata?.name,
            deployTag = runningRc?.deployTag,
            paused = deployment.spec.paused ?: false,
            phase = phase,
            updatedBy = deployment.updatedBy,
            scaledDown = deployment.metadata.labels["scale-down"],
        )
    }

    private suspend fun findDeployResultForDeployment(
        deployment: Deployment,
        namespace: String,
    ): DeploymentResult {
        val replicaSets = client.getReplicaSets(
            namespace,
            mapOf("app" to deployment.metadata.name)
        ).sortedByDescending {
            it.revision
        }
        val runningReplicaSet = replicaSets.firstOrNull {
            it.isRunning()
        }
        val deployDetails = createDeployDetails(deployment, runningReplicaSet)
        @Suppress("DuplicatedCode")
        val imageDetails = runningReplicaSet?.let {
            val image = runningReplicaSet.spec.template.spec.containers[0].image

            when {
                image.substring(0, 2).toIntOrNull() != null -> {
                    ImageDetails(image)
                }
                else -> {
                    runCatching {
                        imageService.getCachedOrFind(image)
                    }.onFailure {
                        logger.warn(
                            "Failed getting imageDetails for " +
                                "namespace=${deployment.metadata.namespace} " +
                                "name=${deployment.metadata.name} " +
                                "image=$image",
                            it
                        )
                    }.getOrNull()
                }
            }
        }

        return DeploymentResult(deployDetails, imageDetails, deployment.spec.selector.matchLabels)
    }

    private fun createDisabledApplication(applicationDeployment: ApplicationDeployment): ApplicationData {
        val auroraStatus = AuroraStatus(OFF)
        val apd = applicationPublicData(applicationDeployment, auroraStatus)

        return applicationData(applicationDeployment, apd)
    }

    fun ReplicationController.isRunning() = this.deploymentPhase == "Complete" &&
        this.status.availableReplicas?.let { it > 0 } ?: false &&
        this.status.replicas?.let { it > 0 } ?: false

    fun ReplicaSet.isRunning() = this.status.availableReplicas?.let { it > 0 } ?: false &&
        this.status.replicas?.let { it > 0 } ?: false
}

@Suppress("DuplicatedCode")
fun List<DeploymentCondition>.findPhase(scalingLimit: Duration, time: Instant): String? {
    val progressing = this.find { it.type == "Progressing" } ?: return null // TODO nodeploy
    val availabilityPhase = this.find { it.type == "Available" }?.findAvailableStatus(scalingLimit, time)
    val progressingStatus = progressing.findProgressingStatus()

    if (progressingStatus != "Complete") return progressingStatus

    return availabilityPhase
}

@Suppress("DuplicatedCode")
fun DeploymentCondition.findAvailableStatus(limit: Duration, time: Instant): String {
    if (this.status.lowercase(getDefault()) != "false") return "Complete"

    val updatedAt = Instant.parse(this.lastUpdateTime)
    val duration = Duration.between(updatedAt, time)

    return when {
        duration > limit -> "Complete"
        else -> "Scaling"
    }
}

@Suppress("DuplicatedCode")
fun DeploymentCondition.findProgressingStatus(): String {
    if (this.status.lowercase(getDefault()) == "false") return "Failed"

    return when (this.reason) {
        "NewReplicaSetAvailable", "NewReplicationControllerAvailable" -> "Complete"
        else -> "DeploymentProgressing"
    }
}
