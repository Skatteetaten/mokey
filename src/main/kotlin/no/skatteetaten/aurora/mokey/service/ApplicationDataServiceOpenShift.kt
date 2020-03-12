package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition
import io.fabric8.kubernetes.api.model.apps.ReplicaSet
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deployTag
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.revision
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.extensions.updatedBy
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
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
            return client.getAllNamespaces().map {
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

    private suspend fun createApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
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
            deployDetails = deployDetails,
            sprocketDone = deployment.sprocketDone,
            updatedBy = deployment.updatedBy
        )
    }

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

    private fun createDeployDetails(
        dc: Deployment,
        runningRc: ReplicaSet?,
        deploymentPhase: String?
    ): DeployDetails {
        return DeployDetails(
            targetReplicas = dc.spec.replicas,
            availableReplicas = dc.status.availableReplicas ?: 0,
            deployment = runningRc?.metadata?.name,
            deployTag = runningRc?.deployTag,
            paused = dc.spec.paused ?: false,
            phase = deploymentPhase
        )
    }

    fun ReplicationController.isRunning() =
        this.deploymentPhase == "Complete" && this.status.availableReplicas?.let { it > 0 } ?: false && this.status.replicas?.let { it > 0 } ?: false

    fun ReplicaSet.isRunning() =
        this.status.availableReplicas?.let { it > 0 } ?: false && this.status.replicas?.let { it > 0 } ?: false
}
