package no.skatteetaten.aurora.mokey.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.DataSources.CLUSTER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@ApplicationDataSource(CLUSTER)
class ApplicationDataServiceOpenShift(
    val openshiftService: OpenShiftService,
    val auroraStatusCalculator: AuroraStatusCalculator,
    val podService: PodService,
    val meterRegistry: MeterRegistry,
    val addressService: AddressService,
    val imageService: ImageService
) : ApplicationDataService {
    override fun findAllVisibleAffiliations(): List<String> {
        throw NotImplementedError("findAllVisibleAffiliations is not supported")
    }

    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceOpenShift::class.java)

    override fun findAllAffiliations(): List<String> {
        return findAllEnvironments().map { it.affiliation }.toSet().toList()
    }

    override fun findAllApplicationData(affiliations: List<String>, ids: List<String>): List<ApplicationData> {
        logger.debug("finding application for affiliations=$affiliations")
        val apps = if (affiliations.isEmpty()) {
            logger.debug("finding applications in all envs")
            findAllApplicationDataByEnvironments()
        } else {
            val allEnvironments = findAllEnvironments()
            val environmentsForAffiliations = allEnvironments.filter { affiliations.contains(it.affiliation) }
            findAllApplicationDataByEnvironments(environmentsForAffiliations)
        }
        return apps.filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }
    }

    override fun findAllPublicApplicationData(affiliations: List<String>, ids: List<String>): List<ApplicationPublicData> {
        throw NotImplementedError("findAllPublicApplicationDataByApplicationDeploymentId is not supported")
    }

    override fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData? {
        throw NotImplementedError("findPublicApplicationDataByApplicationDeploymentId is not supported")
    }

    override fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? {
        throw NotImplementedError("findApplicationDataByApplicationDeploymentId is not supported")
    }

    fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
    }

    private fun findAllApplicationDataByEnvironments(environments: List<Environment> = findAllEnvironments()): List<ApplicationData> {

        logger.debug("finding all applications in environments=$environments")
        return runBlocking(mtContext) {
            val applicationDeployments = environments.flatMap { environment ->
                logger.debug("Finding ApplicationDeployments in namespace={}", environment)
                openshiftService.applicationDeployments(environment.namespace)
            }

            val results = applicationDeployments.map {
                async(mtContext) { tryCreateApplicationData(it) }
            }.map {
                it.await()
            }

            val errors = results.mapNotNull { it.error }

            val data = results.mapNotNull { it.applicationData }
            data.groupBy { it.applicationDeploymentId }.filter { it.value.size != 1 }.forEach {
                val names = it.value.map { "${it.namespace}/${it.applicationDeploymentName}" }
                logger.info("Duplicate applicationDeploymeentId for=$names")
            }

            logger.info("Found deployments=${applicationDeployments.size} data=${data.size} result=${results.size} errors=${errors.size}")
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
            ).also { it.applicationData?.registerAuroraStatusMetrics() }
        } catch (e: Exception) {
            logger.error(
                "Failed getting deployment name={}, namespace={} message={}", it.metadata.name,
                it.metadata.namespace, e.message, e
            )
            MaybeApplicationData(applicationDeployment = it, error = e)
        }
    }

    /**
     * TODO: The call to registerAuroraStatusMetrics is very awkwardly done. Metrics are correctly registered now, but
     * only accidentally because the tryCreateApplicationData method is regularly called. Metrics registration should
     * be done in a more deterministic way.
     */
    private fun ApplicationData.registerAuroraStatusMetrics() {
        this.apply {
            val commonTags = listOf(
                Tag.of("aurora_version", imageDetails?.auroraVersion ?: ""),
                Tag.of("aurora_namespace", namespace),
                Tag.of("aurora_environment", deploymentCommand.applicationDeploymentRef.environment),
                Tag.of("aurora_deployment", applicationDeploymentName),
                Tag.of("aurora_affiliation", affiliation ?: ""),
                Tag.of("aurora_version_strategy", deployTag)
            )

            meterRegistry.gauge("aurora_status", commonTags, auroraStatus.level.level)
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

        // TODO: Akkurat nå støtter vi kun DC.
        val dc = openshiftService.dc(namespace, openShiftName) ?: throw OpenShiftException("Could not fetch DC")
        val deployDetails = dc.let {
            val latestVersion = it.status.latestVersion ?: null
            val phase = latestVersion
                ?.let { version -> openshiftService.rc(namespace, "$openShiftName-$version")?.deploymentPhase }
            DeployDetails(phase, it.spec.replicas, it.status.availableReplicas ?: 0)
        }

        val pods = podService.getPodDetails(applicationDeployment)
        val imageDetails = imageService.getImageDetails(dc)
        val applicationAddresses = addressService.getAddresses(namespace, openShiftName)

        val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

        val splunkIndex = applicationDeployment.spec.splunkIndex

        return ApplicationData(
            booberDeployId = applicationDeployment.metadata.booberDeployId,
            managementPath = applicationDeployment.spec.managementPath,
            pods = pods,
            imageDetails = imageDetails,
            deployDetails = deployDetails,
            addresses = applicationAddresses,
            sprocketDone = dc.sprocketDone,
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
                deployTag = applicationDeployment.spec.deployTag ?: "",
                dockerImageRepo = imageDetails?.dockerImageRepo,
                releaseTo = applicationDeployment.spec.releaseTo
            )
        )
    }
}