package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.extensions.booberDeployId
import no.skatteetaten.aurora.mokey.extensions.deployTag
import no.skatteetaten.aurora.mokey.extensions.deploymentPhase
import no.skatteetaten.aurora.mokey.extensions.managementPath
import no.skatteetaten.aurora.mokey.extensions.sprocketDone
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.DataSources.CLUSTER
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
@ApplicationDataSource(CLUSTER)
class ApplicationDataServiceOpenShift(val openshiftService: OpenShiftService,
                                      val auroraStatusCalculator: AuroraStatusCalculator,
                                      val podService: PodService,
                                      val addressService: AddressService,
                                      val imageService: ImageService) : ApplicationDataService {

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

    override fun findApplicationDataById(id: String): ApplicationData? {
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
                        val deploymentConfigs = openshiftService.deploymentConfigs(environment.namespace)
                        deploymentConfigs.map { dc -> async(mtContext) { createApplicationData(dc) } }
                    }
                    .map { it.await() }
        }
    }

    private fun createApplicationData(dc: DeploymentConfig): ApplicationData {

        //            val status = AuroraStatusCalculator.calculateStatus(app)
        //            //TODO: Burde vi hatt en annen metrikk for apper som ikke er deployet med Boober?
        //            val commonTags = listOf(
        //                    Tag.of("aurora_version", app.auroraVersion),
        //                    Tag.of("aurora_namespace", app.namespace),
        //                    Tag.of("aurora_name", app.name),
        //                    Tag.of("aurora_affiliation", app.affiliation),
        //                    Tag.of("version_strategy", app.deployTag))
        //
        //            meterRegistry.gauge("aurora_status", commonTags, status.level.level)

        return try {
            tryCreateApplicationData(dc)
        } catch (e: Exception) {
            val namespace = dc.metadata.namespace
            val name = dc.metadata.name
            logger.error("Failed getting application name={}, namepsace={} message={}", name, namespace, e.message, e)
            throw e
        }
    }

    private fun tryCreateApplicationData(dc: DeploymentConfig): ApplicationData {
        val affiliation = dc.affiliation
        val namespace = dc.metadata.namespace
        val name = dc.metadata.name
        val latestVersion = dc.status.latestVersion ?: null


        val services = addressService.getAddresses(namespace, mapOf("app" to name))
        val pods = podService.getPodDetails(dc)
        val imageDetails = imageService.getImageDetails(dc)

        val phase = latestVersion?.let { openshiftService.rc(namespace, "$name-$it")?.deploymentPhase }
        val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)
        val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

        val id = ApplicationId(name, Environment.fromNamespace(namespace, affiliation)).toString()
        return ApplicationData(
                id = id,
                auroraStatus = auroraStatus,
                name = name,
                namespace = namespace,
                deployTag = dc.deployTag,
                booberDeployId = dc.booberDeployId,
                affiliation = affiliation,
                managementPath = dc.managementPath,
                pods = pods,
                imageDetails = imageDetails,
                deployDetails = deployDetails,
                sprocketDone = dc.sprocketDone
        )
    }
}