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
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentId
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

    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceOpenShift::class.java)

    override fun findAllAffiliations(): List<String> {
        return findAllEnvironments().map { it.affiliation }.toSet().toList()
    }

    override fun findAllApplicationData(affiliations: List<String>?): List<ApplicationData> {

        return if (affiliations == null)
            findAllApplicationDataByEnvironments()
        else findAllApplicationDataByEnvironments(findAllEnvironments().filter { affiliations.contains(it.affiliation) })
    }

    override fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? {
        val applicationDeploymentId: ApplicationDeploymentId = ApplicationDeploymentId.fromString(id)
        return findAllApplicationDataByEnvironments(listOf(applicationDeploymentId.environment)).find { it.name == applicationDeploymentId.name }
    }

    fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
    }

    private fun findAllApplicationDataByEnvironments(environments: List<Environment> = findAllEnvironments()): List<ApplicationData> {
        return runBlocking(mtContext) {
            environments
                .flatMap { environment ->
                    logger.debug("Find all applications in namespace={}", environment)
                    val applicationDeployments = openshiftService.applicationDeployments(environment.namespace)
                    applicationDeployments.map { applicationDeployment ->
                        async(mtContext) {
                            createApplicationData(
                                applicationDeployment
                            )
                        }
                    }
                }
                .map { it.await() }
        }
    }

    private fun createApplicationData(app: ApplicationDeployment): ApplicationData {
        return try {
            val ad = tryCreateApplicationData(app)
            val commonTags = listOf(
                Tag.of("aurora_version", ad.imageDetails?.auroraVersion ?: ""),
                Tag.of("aurora_namespace", ad.namespace),
                Tag.of("aurora_environment", ad.deploymentCommand.applicationDeploymentRef.environment),
                Tag.of("aurora_deployment", ad.name),
                Tag.of("aurora_affiliation", ad.affiliation ?: ""),
                Tag.of("aurora_version_strategy", ad.deployTag)
            )

            meterRegistry.gauge("aurora_status", commonTags, ad.auroraStatus.level.level)

            ad
        } catch (e: Exception) {
            val namespace = app.metadata.namespace
            val name = app.metadata.name
            logger.error("Failed getting application name={}, namespace={} message={}", name, namespace, e.message, e)
            throw e
        }
    }

    private fun tryCreateApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
        val affiliation = applicationDeployment.metadata.affiliation
        val namespace = applicationDeployment.metadata.namespace
        val name = applicationDeployment.metadata.name
        val pods = podService.getPodDetails(applicationDeployment)
        val applicationAddresses = addressService.getAddresses(namespace, name)

        // TODO: Akkurat nå støtter vi kun DC.
        val dc = openshiftService.dc(namespace, name) ?: throw RuntimeException("Could not fetch DC")
        val latestVersion = dc.status.latestVersion ?: null
        val imageDetails = imageService.getImageDetails(dc)
        val phase = latestVersion?.let { openshiftService.rc(namespace, "$name-$it")?.deploymentPhase }
        val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)

        val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

        val splunkIndex = applicationDeployment.spec.splunkIndex

        return ApplicationData(
            applicationId = applicationDeployment.spec.applicationId,
            applicationDeploymentId = applicationDeployment.spec.applicationDeploymentId,
            auroraStatus = auroraStatus,
            name = name,
            namespace = namespace,
            deployTag = applicationDeployment.spec.deployTag ?: "",
            booberDeployId = applicationDeployment.metadata.booberDeployId,
            affiliation = affiliation,
            managementPath = applicationDeployment.spec.managementPath,
            pods = pods,
            imageDetails = imageDetails,
            deployDetails = deployDetails,
            addresses = applicationAddresses,
            sprocketDone = dc.sprocketDone,
            splunkIndex = splunkIndex,
            deploymentCommand = applicationDeployment.spec.command
        )
    }
}