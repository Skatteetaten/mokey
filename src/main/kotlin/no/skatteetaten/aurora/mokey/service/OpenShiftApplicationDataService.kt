package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.openshift.api.model.DeploymentConfig
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.model.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException

@Service
class OpenShiftApplicationDataService(val openshiftService: OpenShiftService,
                                      val auroraStatusCalculator: AuroraStatusCalculator,
                                      val managmentApplicationService: ManagmentApplicationService,
                                      val mapper: ObjectMapper) {

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
            val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

            val pods = getAllPodDetails(dc)

            val imageDetails = getImageDetails(dc)

            val phase = getDeploymentPhaseFromReplicationController(namespace, name, dc.status.latestVersion)
            val deployDetails = DeployDetails(phase, dc.spec.replicas, dc.status.availableReplicas ?: 0)
            val auroraStatus = auroraStatusCalculator.calculateStatus(deployDetails, pods)

            return ApplicationData(
                    applicationId = ApplicationId(name, Environment.fromNamespace(namespace, affiliation)),
                    auroraStatus = auroraStatus,
                    name = name,
                    namespace = namespace,
                    deployTag = dc.metadata.labels["deployTag"] ?: "",
                    booberDeployId = dc.metadata.labels["booberDeployId"],
                    affiliation = affiliation,
                    managementPath = managementPath,
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

    fun getAllPodDetails(dc: DeploymentConfig): List<PodDetails> {
        val annotations = dc.metadata.annotations ?: emptyMap()
        val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

        val labelMap = dc.spec.selector.mapValues { it.value }
        val pods = openshiftService.pods(dc.metadata.namespace, labelMap)
                .map { getPodDetails(it, managementPath) }
        return pods
    }

    private fun getPodDetails(it: Pod, managementPath: String?): PodDetails {

        val podIP = it.status.podIP ?: null
        val managementData = if (managementPath != null && podIP != null) getManagementData(podIP, managementPath) else null

        val status = it.status.containerStatuses.first()
        return PodDetails(
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

    private fun getManagementData(podIP: String, managementPath: String): ManagementData {

        val violationRules: MutableSet<String> = mutableSetOf()
        val links = getManagementLinks(podIP, managementPath, violationRules)
        val info = getInfoEndpointResponse(links, violationRules)
        val health = getHealthEndpointResponse(links, violationRules)
        val managementData = ManagementData(links, info, health)

        return managementData
    }

    private fun getHealthEndpointResponse(links: Map<String, String>?, violationRules: MutableSet<String>): JsonNode? {
        val health = try {
            links?.let { managmentApplicationService.findResource(links["health"]) }
        } catch (e: HttpStatusCodeException) {
            if (e.statusCode.is5xxServerError) {
                try {
                    mapper.readTree(e.responseBodyAsByteArray)
                } catch (e: Exception) {
                    violationRules.add("MANAGEMENT_HEALTH_ERROR_INVALID_JSON")
                    null
                }
            } else {
                violationRules.add("MANAGEMENT_HEALTH_ERROR_${e.statusCode}")
                null
            }
        } catch (e: RestClientException) {
            violationRules.add("MANAGEMENT_HEALTH_ERROR_HTTP")
            null
        }
        return health
    }

    private fun getInfoEndpointResponse(links: Map<String, String>?, violationRules: MutableSet<String>): JsonNode? {
        val info: JsonNode? = try {
            links?.let { managmentApplicationService.findResource(links["info"]) }
        } catch (e: HttpStatusCodeException) {
            violationRules.add("MANAGEMENT_INFO_ERROR_${e.statusCode}")
            null
        } catch (e: RestClientException) {
            violationRules.add("MANAGEMENT_INFO_ERROR_HTTP")
            null
        }
        return info
    }

    private fun getManagementLinks(podIP: String, managementPath: String, violationRules: MutableSet<String>): Map<String, String>? {
        return try {
            managmentApplicationService.findManagementLinks(podIP, managementPath).also {
                if (it.isEmpty()) {
                    violationRules.add("MANAGEMENT_ENDPOINT_NOT_VALID_FORMAT")
                }
            }
        } catch (e: HttpStatusCodeException) {
            violationRules.add("MANAGEMENT_ENDPOINT_ERROR_${e.statusCode}")
            null
        } catch (e: Exception) {
            violationRules.add("MANAGEMENT_ENDPOINT_ERROR_HTTP")
            null
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