package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest

@Service
class OpenShiftApplicationDataService(val openshiftService: OpenShiftService,
                                      val auroraStatusCalculator: AuroraStatusCalculator,
                                      val managementEndpointFactory: ManagementEndpointFactory) {

    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    val logger: Logger = LoggerFactory.getLogger(OpenShiftApplicationDataService::class.java)

    fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
    }

    fun findAllApplications(environments: List<Environment> = findAllEnvironments()): List<ApplicationData> {
        return runBlocking(mtContext) {
            val map = environments
                    .flatMap { environment ->
                        logger.debug("Find all applications in namespace={}", environment)
                        val deploymentConfigs = openshiftService.deploymentConfigs(environment.namespace)
                        deploymentConfigs.map { dc -> async(mtContext) { createApplicationData(dc) } }
                    }
                    .map { it.await() }
            map
        }
    }

    fun createApplicationData(dc: DeploymentConfig): ApplicationData {

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

        val affiliation = dc.metadata.labels["affiliation"]
        val namespace = dc.metadata.namespace
        val name = dc.metadata.name

        val annotations = dc.metadata.annotations ?: emptyMap()

        try {
            val pods = getPodDetails(dc)

            val imageDetails = getImageDetails(dc)

            val phase = getDeploymentPhaseFromReplicationController(namespace, name, dc.status.latestVersion)
            val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)
            val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

            val id = ApplicationId(name, Environment.fromNamespace(namespace, affiliation)).toString().sha256("apsldga019238")
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
        } catch (e: Exception) {
            logger.error("Failed getting application name={}, namepsace={} message={}", name, namespace, e.message, e)
            throw e
        }
    }

    fun getImageDetails(dc: DeploymentConfig): ImageDetails? {

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

    fun getPodDetails(dc: DeploymentConfig): List<PodDetails> {
        val annotations = dc.metadata.annotations ?: emptyMap()
        val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

        val labelMap = dc.spec.selector.mapValues { it.value }
        return openshiftService.pods(dc.metadata.namespace, labelMap).map {
            val podIP = it.status.podIP ?: null
            val managementData = if (managementPath == null || podIP == null) null else {
                val managementEndpoint = managementEndpointFactory.create(podIP, managementPath)
                managementEndpoint.getManagementData()
            }
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

    fun getDeploymentPhaseFromReplicationController(namespace: String, name: String, versionNumber: Long?): String? {

        if (versionNumber == null) {
            return null
        }

        val rcName = "$name-$versionNumber"
        //TODO: ReplicaSet vs ReplicationController
        return openshiftService.rc(namespace, rcName)?.let {
            it.metadata.annotations["openshift.io/deployment.phase"]
        }
    }
}

private fun String.sha256(salt: String): String {
    val HEX_CHARS = "0123456789ABCDEF"
    val bytes = MessageDigest
            .getInstance("SHA-256")
            .digest((this + salt).toByteArray())
    val result = StringBuilder(bytes.size * 2)
    bytes.forEach {
        val i = it.toInt()
        result.append(HEX_CHARS[i shr 4 and 0x0f])
        result.append(HEX_CHARS[i and 0x0f])
    }
    return result.toString()
}