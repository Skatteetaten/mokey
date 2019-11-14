package no.skatteetaten.aurora.mokey.service

import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deployTag
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.extensions.updatedBy
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataServiceOpenShift(
    val openshiftService: OpenShiftService,
    val auroraStatusCalculator: AuroraStatusCalculator,
    val podService: PodService,
    val addressService: AddressService,
    val imageService: ImageService
) {

    fun findAndGroupAffiliations(affiliations: List<String> = emptyList()): Map<String, List<Environment>> {
        fun findAllEnvironments(): List<Environment> {
            return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
        }
        return findAllEnvironments().filter {
            if (affiliations.isNotEmpty()) {
                affiliations.contains(it.affiliation)
            } else true
        }.groupBy { it.affiliation }
    }

    fun findAllApplicationDataForEnv(
        environments: List<Environment>,
        ids: List<String> = emptyList()
    ): List<ApplicationData> {
        return findAllApplicationDataByEnvironments(environments)
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }
    }

    private fun findAllApplicationDataByEnvironments(environments: List<Environment>): List<ApplicationData> {

        logger.debug("finding all applications in environments=$environments")
        return runBlocking(MDCContext()) {
            val applicationDeployments = environments.flatMap { environment ->
                logger.debug("Finding ApplicationDeployments in namespace={}", environment)
                openshiftService.applicationDeployments(environment.namespace)
            }

            val results = applicationDeployments.map {
                async(Dispatchers.IO) { tryCreateApplicationData(it) }
            }.map {
                it.await()
            }

            val errors = results.mapNotNull { it.error }

            val data = results.mapNotNull { it.applicationData }
            data.groupBy { it.applicationDeploymentId }.filter { it.value.size != 1 }.forEach { data ->
                val names = data.value.map { "${it.namespace}/${it.applicationDeploymentName}" }
                logger.debug("Duplicate applicationDeploymeentId for=$names")
            }

            logger.debug("Found deployments=${applicationDeployments.size} data=${data.size} result=${results.size} errors=${errors.size}")
            data
        }
    }

    fun createSingleItem(namespace: String, name: String): ApplicationData {
        val applicationDeployment = openshiftService.applicationDeployment(namespace, name)
        tryCreateApplicationData(applicationDeployment).applicationData?.let {
            return it
        } ?: throw tryCreateApplicationData(applicationDeployment).error!!
    }

    private data class MaybeApplicationData(
        val applicationDeployment: ApplicationDeployment,
        val applicationData: ApplicationData? = null,
        val error: Exception? = null
    )

    private fun tryCreateApplicationData(it: ApplicationDeployment): MaybeApplicationData {
        return try {
            MaybeApplicationData(
                applicationDeployment = it,
                applicationData = createApplicationData(it)
            )
        } catch (e: Exception) {
            logger.error(
                "Failed getting deployment name={}, namespace={} message={}", it.metadata.name,
                it.metadata.namespace, e.message, e
            )
            MaybeApplicationData(applicationDeployment = it, error = e)
        }
    }

    private fun createApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
        logger.debug("creating application data for deployment=${applicationDeployment.metadata.name} namespace ${applicationDeployment.metadata.namespace}")
        val affiliation = applicationDeployment.metadata.affiliation
        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name
        val applicationDeploymentName = applicationDeployment.spec.applicationDeploymentName
            ?: throw OpenShiftObjectException("applicationDeploymentName was not set for deployment $namespace/$openShiftName")
        val applicationName = applicationDeployment.spec.applicationName
            ?: throw OpenShiftObjectException("applicationName was not set for deployment $namespace/$openShiftName")

        val databases = applicationDeployment.spec.databases ?: listOf()

        val dc = openshiftService.dc(namespace, openShiftName)
        if (dc == null) {
            val auroraStatus = AuroraStatus(AuroraStatusLevel.OFF)
            return ApplicationData(
                booberDeployId = applicationDeployment.metadata.booberDeployId,
                managementPath = applicationDeployment.spec.managementPath,
                deploymentCommand = applicationDeployment.spec.command,
                databases = databases,
                publicData = ApplicationPublicData(
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
            )
        }

        val deployDetails = createDeployDetails(dc)
        // Using dc.spec.selector to find matching pods. Should be selector from ApplicationDeployment, but since not
        // every pods has a name label we have to use selector from DeploymentConfig.
        val pods = podService.getPodDetails(applicationDeployment, deployDetails, dc.spec.selector)

        val imageDetails = imageService.getImageDetails(dc)
        val applicationAddresses = addressService.getAddresses(namespace, openShiftName)

        val auroraStatus = auroraStatusCalculator.calculateAuroraStatus(deployDetails, pods)

        val splunkIndex = applicationDeployment.spec.splunkIndex

        fun getDeployTag(): String {
            if (deployDetails.deployTag != null && deployDetails.deployTag != "") return deployDetails.deployTag
            else if (applicationDeployment.spec.deployTag != null) return applicationDeployment.spec.deployTag
            return ""
        }

        return ApplicationData(
            booberDeployId = applicationDeployment.metadata.booberDeployId,
            managementPath = applicationDeployment.spec.managementPath,
            pods = pods,
            imageDetails = imageDetails,
            deployDetails = deployDetails,
            addresses = applicationAddresses,
            databases = databases,
            sprocketDone = dc.sprocketDone,
            updatedBy = dc.updatedBy,
            splunkIndex = splunkIndex,
            deploymentCommand = applicationDeployment.spec.command,
            publicData = ApplicationPublicData(
                applicationId = applicationDeployment.spec.applicationId,
                applicationDeploymentId = applicationDeployment.spec.applicationDeploymentId,
                auroraStatus = auroraStatus,
                applicationName = applicationName,
                applicationDeploymentName = applicationDeploymentName,
                namespace = namespace,
                affiliation = affiliation,
                auroraVersion = imageDetails?.auroraVersion,
                deployTag = getDeployTag(),
                dockerImageRepo = imageDetails?.dockerImageRepo,
                releaseTo = applicationDeployment.spec.releaseTo,
                message = applicationDeployment.spec.message,
                environment = applicationDeployment.spec.command.applicationDeploymentRef.environment
            )
        )
    }

    private fun createDeployDetails(dc: DeploymentConfig): DeployDetails {

        val namespace = dc.metadata.namespace

        fun getReplicationController(rcVersion: Long): ReplicationController? {
            val rcName = "${dc.metadata.name}-$rcVersion"
            return openshiftService.rc(namespace, rcName)
        }

        val details = DeployDetails(
            targetReplicas = dc.spec.replicas,
            availableReplicas = dc.status.availableReplicas ?: 0,
            paused = dc.spec.paused ?: false
        )

        for (rcVersion in dc.status.latestVersion downTo 0) {
            getReplicationController(rcVersion)?.let {
                if (it.isRunning()) {
                    return details.copy(
                        deployment = it.metadata.name,
                        phase = it.deploymentPhase,
                        deployTag = it.deployTag
                    )
                }
            }
        }
        return details
    }

    fun ReplicationController.isRunning() =
        this.deploymentPhase == "Complete" && this.status.availableReplicas > 0 && this.status.replicas > 0
}