package no.skatteetaten.aurora.mokey.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.async
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
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

    val mtContext = newFixedThreadPoolContext(6, "mokeyPool")

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceOpenShift::class.java)

    override fun findAllAffiliations(): List<String> {
        return findAndGroupAffiliations().keys.toList()
    }

    override fun findAllApplicationData(affiliations: List<String>, ids: List<String>): List<ApplicationData> {
        val affiliationGroups = findAndGroupAffiliations(affiliations)
        return findAllApplicationDataForEnv(ids, affiliationGroups)
    }

    override fun findAllVisibleAffiliations(): List<String> {
        throw NotImplementedError("findAllVisibleAffiliations is not supported")
    }

    override fun findAllPublicApplicationData(
        affiliations: List<String>,
        ids: List<String>
    ): List<ApplicationPublicData> {
        throw NotImplementedError("findAllPublicApplicationDataByApplicationDeploymentId is not supported")
    }

    override fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData? {
        throw NotImplementedError("findPublicApplicationDataByApplicationDeploymentId is not supported")
    }

    override fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? {
        throw NotImplementedError("findApplicationDataByApplicationDeploymentId is not supported")
    }

    fun findAndGroupAffiliations(affiliations: List<String> = emptyList()): Map<String, List<Environment>> {
        return findAllEnvironments().filter {
            if (affiliations.isNotEmpty()) {
                affiliations.contains(it.affiliation)
            } else true
        }.groupBy { it.affiliation }
    }

    fun findAllApplicationDataForEnv(
        ids: List<String>,
        affiliationEnvs: Map<String, List<Environment>>
    ): List<ApplicationData> {
        return affiliationEnvs.flatMap {
            findAllApplicationDataForEnv(it.value, ids)
        }
    }

    fun findAllApplicationDataForEnv(
        environments: List<Environment>,
        ids: List<String> = emptyList()
    ): List<ApplicationData> {
        return findAllApplicationDataByEnvironments(environments)
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }
    }

    fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
    }

    private fun findAllApplicationDataByEnvironments(environments: List<Environment>): List<ApplicationData> {

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

        val dc = openshiftService.dc(namespace, openShiftName)

        if (dc == null) {
            return ApplicationData(
                booberDeployId = applicationDeployment.metadata.booberDeployId,
                managementPath = applicationDeployment.spec.managementPath,
                deploymentCommand = applicationDeployment.spec.command,
                publicData = ApplicationPublicData(
                    applicationId = applicationDeployment.spec.applicationId,
                    applicationDeploymentId = applicationDeployment.spec.applicationDeploymentId,
                    auroraStatus = AuroraStatus(AuroraStatusLevel.OFF, "No Deployment Config found"),
                    applicationName = applicationName,
                    applicationDeploymentName = applicationDeploymentName,
                    namespace = namespace,
                    affiliation = affiliation,
                    deployTag = applicationDeployment.spec.deployTag ?: "",
                    releaseTo = applicationDeployment.spec.releaseTo
                )
            )
        }
        // TODO er dette alltid den som kjører?
        val latestVersion = dc.status.latestVersion ?: null
        val phase = latestVersion
            ?.let { version -> openshiftService.rc(namespace, "$openShiftName-$version")?.deploymentPhase }
        // TODO her må vi ha med alle containerene og image fra dem.
        val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)

        val pods = podService.getPodDetails(applicationDeployment)
        // TODO: pod sin deployment er aktiv rc version ikke den som står i dc.
        // TODO: Hvis siste rc ikke er den som kjører. Må ha med imageId for den som feiler
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