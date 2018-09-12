package no.skatteetaten.aurora.mokey.service

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
        return findAllApplicationDataByEnvironments(listOf(applicationDeploymentId.environment)).find { it.applicationDeploymentName == applicationDeploymentId.name }
    }

    fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
    }

    private fun findAllApplicationDataByEnvironments(environments: List<Environment> = findAllEnvironments()): List<ApplicationData> {

        return runBlocking(mtContext) {
            environments
                .flatMap { environment ->
                    val deployments = openshiftService.applicationDeployments(environment.namespace)
                    logger.debug("Found {} ApplicationDeployments in namespace={}", deployments.size, environment)
                    deployments.map { async(mtContext) { tryCreateApplicationData(it) } }
                }.mapNotNull { it.await().applicationData }
        }
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
                "Failed getting application name={}, namespace={} message={}", it.metadata.name,
                it.metadata.namespace, e.message, e
            )
            MaybeApplicationData(applicationDeployment = it, error = e)
        }
    }

    private fun createApplicationData(applicationDeployment: ApplicationDeployment): ApplicationData {
        val affiliation = applicationDeployment.metadata.affiliation
        val namespace = applicationDeployment.metadata.namespace
        val openshiftName = applicationDeployment.metadata.name
        val applicationDeploymentName = applicationDeployment.spec.applicationDeploymentName
            ?: openshiftName
        val applicationName = applicationDeployment.spec.applicationName ?: openshiftName

        fun isAnyNull(vararg o: Any?): Boolean = o.any { it == null }

        if (applicationDeployment.spec.let { isAnyNull(it.applicationDeploymentName, it.applicationName) }) {
            logger.warn(
                "Required fields applicationName={} or applicationDeploymentName={} was not set for namespace={} " +
                    "name={}. Falling back to defaults.",
                applicationDeployment.spec.applicationName,
                applicationDeployment.spec.applicationDeploymentName,
                namespace,
                openshiftName
            )
        }

        val pods = podService.getPodDetails(applicationDeployment)
        val applicationAddresses = addressService.getAddresses(namespace, openshiftName)

        // TODO: Akkurat nå støtter vi kun DC.
        val dc = openshiftService.dc(namespace, openshiftName) ?: throw RuntimeException("Could not fetch DC")
        val latestVersion = dc.status.latestVersion ?: null
        val imageDetails = imageService.getImageDetails(dc)
        val phase = latestVersion?.let { openshiftService.rc(namespace, "$openshiftName-$it")?.deploymentPhase }
        val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)

        val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

        val splunkIndex = applicationDeployment.spec.splunkIndex

        return ApplicationData(
            applicationId = applicationDeployment.spec.applicationId,
            applicationDeploymentId = applicationDeployment.spec.applicationDeploymentId,
            auroraStatus = auroraStatus,
            applicationName = applicationName,
            applicationDeploymentName = applicationDeploymentName,
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