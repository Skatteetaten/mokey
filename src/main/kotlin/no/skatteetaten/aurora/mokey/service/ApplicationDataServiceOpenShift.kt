package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.AuroraApplicationInstance
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
        else
            findAllApplicationDataByEnvironments(findAllEnvironments().filter { affiliations.contains(it.affiliation) })
    }

    override fun findApplicationDataByInstanceId(id: String): ApplicationData? {
        val applicationId: ApplicationId = ApplicationId.fromString(id)
        return findAllApplicationDataByEnvironments(listOf(applicationId.environment)).find { it.name == applicationId.name }
    }

    fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
    }

    private fun findAllApplicationDataByEnvironments(environments: List<Environment> = findAllEnvironments()): List<ApplicationData> {
        return runBlocking(mtContext) {
            environments
                .flatMap { environment ->
                    logger.debug("Find all applications in namespace={}", environment)
                    val applicationInstances = openshiftService.auroraApplicationInstances(environment.namespace)
                    applicationInstances.map { instance -> async(mtContext) { createApplicationData(instance) } }
                }
                .map { it.await() }
        }
    }

    private fun createApplicationData(app: AuroraApplicationInstance): ApplicationData {

        //            val status = AuroraStatusCalculator.calculateStatus(app)
        //            val commonTags = listOf(
        //                    Tag.of("aurora_version", app.auroraVersion),
        //                    Tag.of("aurora_namespace", app.namespace),
        //                    Tag.of("aurora_name", app.name),
        //                    Tag.of("aurora_affiliation", app.affiliation),
        //                    Tag.of("version_strategy", app.deployTag))
        //
        //            meterRegistry.gauge("aurora_status", commonTags, status.level.level)

        return try {
            tryCreateApplicationData(app)
        } catch (e: Exception) {
            val namespace = app.metadata.namespace
            val name = app.metadata.name
            logger.error("Failed getting application name={}, namespace={} message={}", name, namespace, e.message, e)
            throw e
        }
    }

    private fun tryCreateApplicationData(applicationInstance: AuroraApplicationInstance): ApplicationData {
        val affiliation = applicationInstance.metadata.affiliation
        val namespace = applicationInstance.metadata.namespace
        val name = applicationInstance.metadata.name
        val pods = podService.getPodDetails(applicationInstance)
        val applicationAddresses = addressService.getAddresses(namespace, name)

        // TODO: Akkurat nå støtter vi kun DC.
        val dc = openshiftService.dc(namespace, name) ?: throw RuntimeException("Could not fetch DC")
        val latestVersion = dc.status.latestVersion ?: null
        val imageDetails = imageService.getImageDetails(dc)
        val phase = latestVersion?.let { openshiftService.rc(namespace, "$name-$it")?.deploymentPhase }
        val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)

        val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

        val splunkIndex = applicationInstance.spec.splunkIndex

        return ApplicationData(
            applicationId = applicationInstance.spec.applicationId,
            applicationInstanceId = applicationInstance.spec.applicationInstanceId,
            auroraStatus = auroraStatus,
            name = name,
            namespace = namespace,
            deployTag = applicationInstance.spec.deployTag ?: "",
            booberDeployId = applicationInstance.metadata.booberDeployId,
            affiliation = affiliation,
            managementPath = applicationInstance.spec.managementPath,
            pods = pods,
            imageDetails = imageDetails,
            deployDetails = deployDetails,
            addresses = applicationAddresses,
            sprocketDone = dc.sprocketDone,
            splunkIndex = splunkIndex,
            command = applicationInstance.spec.command
        )
    }
}