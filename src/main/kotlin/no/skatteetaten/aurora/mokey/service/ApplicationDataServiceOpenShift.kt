package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationId
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ApplicationDataServiceOpenShift(val openshiftService: OpenShiftService,
                                      val auroraStatusCalculator: AuroraStatusCalculator,
                                      val managementDataService: ManagementDataService) {

    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceOpenShift::class.java)

    fun findAllApplicationData(affiliations: List<String>?): List<ApplicationData> {

        return if (affiliations == null)
            findAllApplicationDataByEnvironments()
        else
            findAllApplicationDataByEnvironments(findAllEnvironments().filter { affiliations.contains(it.affiliation) })
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
        val affiliation = dc.metadata.labels["affiliation"]
        val namespace = dc.metadata.namespace
        val name = dc.metadata.name

        val annotations = dc.metadata.annotations ?: emptyMap()
        val pods = getPodDetails(dc)

        val imageDetails = getImageDetails(dc)

        val latestVersion = dc.status.latestVersion ?: null
        val phase = latestVersion?.let { getDeploymentPhaseFromReplicationController(namespace, name, it) }
        val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)
        val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

        val id = ApplicationId(name, Environment.fromNamespace(namespace, affiliation)).toString()
        return ApplicationData(
                id = id,
                auroraStatus = auroraStatus,
                name = name,
                namespace = namespace,
                deployTag = dc.metadata.labels["deployTag"] ?: "",
                booberDeployId = dc.metadata.labels["booberDeployId"],
                affiliation = affiliation,
                managementPath = annotations["console.skatteetaten.no/management-path"],
                pods = pods,
                imageDetails = imageDetails,
                deployDetails = deployDetails,
                sprocketDone = annotations["sprocket.sits.no-deployment-config.done"]
        )
    }

    private fun getImageDetails(dc: DeploymentConfig): ImageDetails? {

        val deployTag = dc.spec.triggers.find { it.type == "ImageChange" }
                ?.imageChangeParams?.from?.name?.split(":")?.lastOrNull()
                ?: return null

        val tag = openshiftService.imageStreamTag(dc.metadata.namespace, dc.metadata.name, deployTag)
        val environmentVariables = tag?.image?.dockerImageMetadata?.containerConfig?.env?.map {
            val (key, value) = it.split("=")
            key to value
        }?.toMap()
        return ImageDetails(tag?.image?.dockerImageReference, environmentVariables ?: mapOf())
    }

    private fun getPodDetails(dc: DeploymentConfig): List<PodDetails> {
        val annotations = dc.metadata.annotations ?: emptyMap()
        val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

        val labelMap = dc.spec.selector.mapValues { it.value }
        return openshiftService.pods(dc.metadata.namespace, labelMap).map {
            val podIP = it.status.podIP ?: null
            val managementData = managementDataService.load(podIP, managementPath)

            val status = it.status.containerStatuses.first()
            PodDetails(
                    OpenShiftPodExcerpt(
                            name = it.metadata.name,
                            status = it.status.phase,
                            restartCount = status.restartCount,
                            ready = status.ready,
                            podIP = podIP,
                            deployment = it.metadata.labels["deployment"],
                            startTime = it.status.startTime
                    ),
                    managementData
            )
        }
    }

    private fun getDeploymentPhaseFromReplicationController(namespace: String, name: String, versionNumber: Long): String? {

        val rcName = "$name-$versionNumber"
        //TODO: ReplicaSet vs ReplicationController
        return openshiftService.rc(namespace, rcName)?.let {
            it.metadata.annotations["openshift.io/deployment.phase"]
        }
    }
}