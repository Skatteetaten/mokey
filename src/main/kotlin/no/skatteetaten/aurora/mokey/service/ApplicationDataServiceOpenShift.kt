package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import io.fabric8.openshift.api.model.DeploymentConfig
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
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.DeploymentResult
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.pmapIO
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataServiceOpenShift(
    val client: OpenShiftServiceAccountClient,
    val auroraStatusCalculator: AuroraStatusCalculator,
    val podService: PodService,
    val addressService: AddressService,
    val imageService: ImageService
) {

    suspend fun findAndGroupAffiliations(affiliations: List<String> = emptyList()): Map<String, List<Environment>> {
        suspend fun findAllEnvironments(): List<Environment> {
            return client.getAllProjects().map {
                Environment.fromNamespace(it.metadata.name)
            }
        }
        return findAllEnvironments().filter {
            if (affiliations.isNotEmpty()) {
                affiliations.contains(it.affiliation)
            } else true
        }.groupBy { it.affiliation }
    }

    suspend fun findAllApplicationDataForEnv(
        environments: List<Environment>,
        ids: List<String> = emptyList()
    ): List<ApplicationData> {
        return findAllApplicationDataByEnvironments(environments)
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }
    }

    private suspend fun findAllApplicationDataByEnvironments(environments: List<Environment>): List<ApplicationData> {

        logger.debug("finding all applications in environments=$environments")
        val applicationDeployments: List<ApplicationDeployment> = environments.flatMap { environment ->
            logger.debug("Finding ApplicationDeployments in namespace={}", environment)
            client.getApplicationDeployments(environment.namespace)
        }

        val results = applicationDeployments.pmapIO { tryCreateApplicationData(it) }

        val errors = results.mapNotNull { it.error }

        val data = results.mapNotNull { it.applicationData }

        logger.debug("Found deployments=${applicationDeployments.size} data=${data.size} result=${results.size} errors=${errors.size}")
        return data
    }

    suspend fun createSingleItem(namespace: String, name: String): ApplicationData {
        val applicationDeployment = client.getApplicationDeployment(name, namespace)

        return tryCreateApplicationData(applicationDeployment).applicationData?.let {
            it
        } ?: throw tryCreateApplicationData(applicationDeployment).error!!
    }

    private data class MaybeApplicationData(
        val applicationDeployment: ApplicationDeployment,
        val applicationData: ApplicationData? = null,
        val error: Exception? = null
    )

    private suspend fun tryCreateApplicationData(it: ApplicationDeployment): MaybeApplicationData {
        return try {
            MaybeApplicationData(
                applicationDeployment = it,
                applicationData = createApplicationData(it)
            )
        } catch (e: Exception) {
            logger.info(
                "Failed getting deployment name=${it.metadata.name}, namespace=${it.metadata.namespace} message=${e.message}",
                e
            )
            MaybeApplicationData(applicationDeployment = it, error = e)
        }
    }

    private fun applicationPublicData(
        applicationDeployment: ApplicationDeployment,
        auroraStatus: AuroraStatus
    ): ApplicationPublicData {
        val affiliation = applicationDeployment.metadata.affiliation
        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name
        val applicationDeploymentName = applicationDeployment.spec.applicationDeploymentName
            ?: throw OpenShiftObjectException("applicationDeploymentName was not set for deployment $namespace/$openShiftName")
        val applicationName = applicationDeployment.spec.applicationName
            ?: throw OpenShiftObjectException("applicationName was not set for deployment $namespace/$openShiftName")

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
            environment = applicationDeployment.spec.command.applicationDeploymentRef.environment
        )
    }

    private fun applicationData(
        applicationDeployment: ApplicationDeployment,
        applicationPublicData: ApplicationPublicData
    ): ApplicationData {
        val databases = applicationDeployment.spec.databases ?: listOf()

        return ApplicationData(
            booberDeployId = applicationDeployment.metadata.booberDeployId,
            managementPath = applicationDeployment.spec.managementPath,
            deploymentCommand = applicationDeployment.spec.command,
            databases = databases,
            publicData = applicationPublicData
        )
    }

    private suspend fun createApplicationData2(applicationDeployment: ApplicationDeployment): ApplicationData {
        logger.debug("creating application data for deployment=${applicationDeployment.metadata.name} namespace ${applicationDeployment.metadata.namespace}")
        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name

        val deployment = client.getDeployment(namespace, openShiftName)

        if (deployment == null) {
            val auroraStatus = AuroraStatus(AuroraStatusLevel.OFF)
            val apd = applicationPublicData(applicationDeployment, auroraStatus)
            return applicationData(applicationDeployment, apd)
        }

        val phase = findDeploymentPhase(deployment)

        val replicaSets =
            client.getReplicaSets(namespace, mapOf("app" to deployment.metadata.name)).sortedByDescending {
                it.revision
            }

        val runningReplicaSet = replicaSets.firstOrNull {
            it.isRunning()
        }

        val deployDetails = createDeployDetails(deployment, runningReplicaSet, phase)

        // TODO: We have more information here that is lost in translation. If there is a failing pod why is it failing. Should this be included?

        // TODO: Should this include failed pods?
        val pods = podService.getPodDetails(applicationDeployment, deployDetails, deployment.spec.selector.matchLabels)

        val imageDetails = runningReplicaSet?.let {
            val image = runningReplicaSet.spec.template.spec.containers[0].image
            if (image.substring(0, 2).toIntOrNull() != null) {
                null
            } else {
                try {
                    imageService.getCachedOrFind(image)
                } catch (e: Exception) {
                    logger.warn(
                        "Failed getting imageDetails for namespace=${deployment.metadata.namespace} name=${deployment.metadata.name} image=$image",
                        e
                    )
                    null
                }
            }
        }

        val applicationAddresses = addressService.getAddresses(namespace, openShiftName)

        val auroraStatus = auroraStatusCalculator.calculateAuroraStatus(deployDetails, pods)

        val splunkIndex = applicationDeployment.spec.splunkIndex

        val deployTag = deployDetails.deployTag.takeIf { !it.isNullOrEmpty() }
            ?: (applicationDeployment.spec.deployTag.let { it } ?: "")

        val apd = applicationPublicData(
            applicationDeployment,
            auroraStatus
        ).copy(
            auroraVersion = imageDetails?.auroraVersion,
            dockerImageRepo = imageDetails?.dockerImageRepo,
            deployTag = deployTag
        )

        return applicationData(applicationDeployment, apd).copy(
            pods = pods,
            imageDetails = imageDetails,
            addresses = applicationAddresses,
            splunkIndex = splunkIndex,
            deployDetails = deployDetails
        )
    }

    // TODO: Refactor
    private suspend fun createApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
        logger.debug("creating application data for deployment=${applicationDeployment.metadata.name} namespace ${applicationDeployment.metadata.namespace}")

        val runnableType = applicationDeployment.spec.runnableType ?: "DeploymentConfig"

        if (runnableType == "Job" || runnableType == "CronJob") {
            return createDisabledApplication(applicationDeployment)
        }

        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name

        val result = if (runnableType == "Deployment") {
            val deployment = client.getDeployment(namespace, openShiftName) ?: return createDisabledApplication(applicationDeployment)
            findDeployResultForDeployment(deployment, namespace)
        } else {
            val dc = client.getDeploymentConfig(namespace, openShiftName)
                ?: return createDisabledApplication(applicationDeployment)

            findDeployResultForDC(namespace, dc)
        }

        val deployDetails = result.details
        val imageDetails = result.image

        val pods = podService.getPodDetails(applicationDeployment, deployDetails, result.selector)

        val applicationAddresses = addressService.getAddresses(namespace, openShiftName)

        val auroraStatus = auroraStatusCalculator.calculateAuroraStatus(deployDetails, pods)

        val splunkIndex = applicationDeployment.spec.splunkIndex

        val deployTag = deployDetails.deployTag.takeIf { !it.isNullOrEmpty() }
            ?: (applicationDeployment.spec.deployTag.let { it } ?: "")

        // TODO: avoid copy here.
        val apd = applicationPublicData(
            applicationDeployment,
            auroraStatus
        ).copy(
            auroraVersion = imageDetails?.auroraVersion,
            dockerImageRepo = imageDetails?.dockerImageRepo,
            deployTag = deployTag
        )

        return applicationData(applicationDeployment, apd).copy(
            pods = pods,
            imageDetails = imageDetails,
            addresses = applicationAddresses,
            splunkIndex = splunkIndex,
            deployDetails = deployDetails
        )
    }

    private suspend fun findDeployResultForDC(
        namespace: String,
        dc: DeploymentConfig
    ): DeploymentResult {
        val replicationControllers =
            client.getReplicationControllers(namespace, mapOf("app" to dc.metadata.name)).sortedByDescending {
                it.metadata.name.substringAfterLast("-").toInt()
            }
        val latestRc = replicationControllers.firstOrNull()

        val runningRc = replicationControllers.firstOrNull {
            it.isRunning()
        }

        val deployDetails = createDeployDetails(dc, runningRc, latestRc?.deploymentPhase)

        // it is a lot faster to fetch from imageStreamTag from ocp rather then from cantus if it is up to date
        val imageDetails: ImageDetails? = if (runningRc == latestRc || runningRc == null) {
            // gets ImageDetails for the first Image that is found in the ImageChange triggers for the given DeploymentConfig
            dc.imageStreamNameAndTag?.let {
                imageService.getImageDetailsFromImageStream(dc.metadata.namespace, it.first, it.second)
            }
        } else {
            val image = runningRc.spec.template.spec.containers[0].image
            if (image.substring(0, 2).toIntOrNull() != null) {
                ImageDetails(image)
            } else {
                try {
                    imageService.getCachedOrFind(image)
                } catch (e: Exception) {
                    logger.warn(
                        "Failed getting imageDetails for namespace=${dc.metadata.namespace} name=${dc.metadata.name} image=$image",
                        e
                    )
                    null
                }
            }
        }
        return DeploymentResult(deployDetails, imageDetails, dc.spec.selector)
    }

    private suspend fun findDeployResultForDeployment(
        deployment: Deployment,
        namespace: String
    ): DeploymentResult {
        val phase = findDeploymentPhase(deployment)

        val replicaSets =
            client.getReplicaSets(namespace, mapOf("app" to deployment.metadata.name)).sortedByDescending {
                it.revision
            }

        val runningReplicaSet = replicaSets.firstOrNull {
            it.isRunning()
        }

        val deployDetails = createDeployDetails(deployment, runningReplicaSet, phase)

        // TODO: We have more information here that is lost in translation. If there is a failing pod why is it failing. Should this be included?
        val imageDetails = runningReplicaSet?.let {
            val image = runningReplicaSet.spec.template.spec.containers[0].image
            if (image.substring(0, 2).toIntOrNull() != null) {
                ImageDetails(image)
            } else {
                try {
                    imageService.getCachedOrFind(image)
                } catch (e: Exception) {
                    logger.warn(
                        "Failed getting imageDetails for namespace=${deployment.metadata.namespace} name=${deployment.metadata.name} image=$image",
                        e
                    )
                    null
                }
            }
        }
        return DeploymentResult(deployDetails, imageDetails, deployment.spec.selector.matchLabels)
    }

    private fun createDisabledApplication(applicationDeployment: ApplicationDeployment): ApplicationData {
        val auroraStatus = AuroraStatus(AuroraStatusLevel.OFF)
        val apd = applicationPublicData(applicationDeployment, auroraStatus)
        return applicationData(applicationDeployment, apd)
    }

    private fun createDeployDetails(
        dc: DeploymentConfig,
        runningRc: ReplicationController?,
        deploymentPhase: String?
    ): DeployDetails {
        return DeployDetails(
            targetReplicas = dc.spec.replicas,
            availableReplicas = dc.status.availableReplicas ?: 0,
            deployment = runningRc?.metadata?.name,
            deployTag = runningRc?.deployTag,
            paused = dc.spec.paused ?: false,
            phase = deploymentPhase,
            updatedBy = dc.updatedBy
        )
    }

    private fun createDeployDetails(
        deployment: Deployment,
        runningRc: ReplicaSet?,
        deploymentPhase: String?
    ): DeployDetails {
        return DeployDetails(
            targetReplicas = deployment.spec.replicas,
            availableReplicas = deployment.status.availableReplicas ?: 0,
            deployment = runningRc?.metadata?.name,
            deployTag = runningRc?.deployTag,
            paused = deployment.spec.paused ?: false,
            phase = deploymentPhase,
            updatedBy = deployment.updatedBy
        )
    }

    fun ReplicationController.isRunning() =
        this.deploymentPhase == "Complete" && this.status.availableReplicas?.let { it > 0 } ?: false && this.status.replicas?.let { it > 0 } ?: false

    fun ReplicaSet.isRunning() =
        this.status.availableReplicas?.let { it > 0 } ?: false && this.status.replicas?.let { it > 0 } ?: false

    private fun findDeploymentPhase(deployment: Deployment): String {
        val progressing = deployment.status.conditions.find { it.type == "Progressing" }

        val phase = progressing?.let {
            findStatus(it)
        } ?: "Complete"
        return phase
    }

    private fun findStatus(it: DeploymentCondition): String {
        if (it.status.toLowerCase() == "false") {
            return "Failed"
        }

        return if (it.reason == "ReplicaSetUpdated") {
            "Ongoing"
        } else {
            "Complete"
        }
    }
}
