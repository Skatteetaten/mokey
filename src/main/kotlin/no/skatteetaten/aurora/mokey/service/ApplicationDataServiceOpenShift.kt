package no.skatteetaten.aurora.mokey.service

import com.fkorotkov.kubernetes.metadata
import com.fkorotkov.kubernetes.newReplicationController
import com.fkorotkov.openshift.metadata
import com.fkorotkov.openshift.newDeploymentConfig
import com.fkorotkov.openshift.newProject
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesClient
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
import no.skatteetaten.aurora.kubernetes.crd.ApplicationDeployment
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deployTag
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.imageStreamNameAndTag
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.extensions.updatedBy
import no.skatteetaten.aurora.mokey.model.*
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataServiceOpenShift(
    //TODO: Hvordan får vi sagt ifra om at denne finnes i kubernetes-client?
    @TargetClient(ClientTypes.SERVICE_ACCOUNT) val client: KubernetesCoroutinesClient,
    val auroraStatusCalculator: AuroraStatusCalculator,
    val podService: PodService,
    val addressService: AddressService,
    val imageService: ImageService
) {

    // TODO: Can we rewrite this to use a label query? If all projects are labels with affiliation now?
    fun findAndGroupAffiliations(affiliations: List<String> = emptyList()): Map<String, List<Environment>> {
        fun findAllEnvironments(): List<Environment> {
            return runBlocking {
                client.getMany(newProject { }).map {
                    Environment.fromNamespace(it.metadata.name)
                }
            }
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
            val applicationDeployments: List<ApplicationDeployment> = environments.flatMap { environment ->
                logger.debug("Finding ApplicationDeployments in namespace={}", environment)
                client.getMany(newApplicationDeployment {
                    metadata {
                        namespace= environment.namespace
                    }
                })
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
        val applicationDeployment = runBlocking {
            client.get(ApplicationDeployment().apply {
                metadata.name = name
                metadata.namespace = namespace
            })
        }
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
            logger.info(
                "Failed getting deployment name={}, namespace={} message={}", it.metadata.name,
                it.metadata.namespace, e.message, e
            )
            MaybeApplicationData(applicationDeployment = it, error = e)
        }
    }

    fun getRunningRc(namespace: String, name: String, rcLatestVersion: Long): ReplicationController? {
        val range: IntProgression = rcLatestVersion.toInt() - 1 downTo 1
        return range.asSequence().map { num ->
            runBlocking {
                client.getOrNull(newReplicationController {
                    metadata {
                        this.namespace = namespace
                        this.name = "$name-$num}"
                    }
                })
            }
        }
            .firstOrNull {
                if (it == null) {
                    return null
                }
                it.isRunning()
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

    private fun createApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
        logger.debug("creating application data for deployment=${applicationDeployment.metadata.name} namespace ${applicationDeployment.metadata.namespace}")
        val namespace = applicationDeployment.metadata.namespace
        val openShiftName = applicationDeployment.metadata.name

        val dc = runBlocking {
            client.getOrNull(newDeploymentConfig {
                metadata {
                    this.namespace = namespace
                    this.name = openShiftName
                }
            })
        }

        if (dc == null) {
            val auroraStatus = AuroraStatus(AuroraStatusLevel.OFF)
            val apd = applicationPublicData(applicationDeployment, auroraStatus)
            return applicationData(applicationDeployment, apd)
        }

        //TOOD: Can we replace this with a call to find all replicationController with a given app= label and then sort them?
        val latestRc = runBlocking {
            client.getOrNull(newReplicationController {
                metadata {
                    this.namespace = namespace
                    this.name = "${dc.metadata.name}-${dc.status.latestVersion}"
                }
            })
        }

        val runningRc = latestRc.takeIf { it?.isRunning() ?: false }
            ?: getRunningRc(namespace, openShiftName, dc.status.latestVersion)

        val deployDetails = createDeployDetails(dc, runningRc, latestRc?.deploymentPhase)

        // Using dc.spec.selector to find matching pods. Should be selector from ApplicationDeployment, but since not
        // every pods has a name label we have to use selector from DeploymentConfig.
        val pods = podService.getPodDetails(applicationDeployment, deployDetails, dc.spec.selector)

        // it is a lot faster to fetch from imageStreamTag from ocp rather then from cantus if it is up to date
        val imageDetails = if (runningRc == latestRc || runningRc == null) {
            // gets ImageDetails for the first Image that is found in the ImageChange triggers for the given DeploymentConfig
            dc.imageStreamNameAndTag?.let {
                imageService.getImageDetailsFromImageStream(dc.metadata.namespace, it.first, it.second)
            }
        } else {
            val image = runningRc.spec.template.spec.containers[0].image
            imageService.getImageDetails(dc.metadata.namespace, dc.metadata.name, image)
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
            sprocketDone = dc.sprocketDone,
            updatedBy = dc.updatedBy
        )
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
            phase = deploymentPhase
        )
    }

    fun ReplicationController.isRunning() =
        this.deploymentPhase == "Complete" && this.status.availableReplicas?.let { it > 0 } ?: false && this.status.replicas?.let { it > 0 } ?: false
}
