package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
                                      val managmentApplicationService: ManagmentApplicationService,
                                      val mapper: ObjectMapper) {

    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    val logger: Logger = LoggerFactory.getLogger(OpenShiftApplicationDataService::class.java)

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

    private fun findAllEnvironments(): List<Environment> {
        return openshiftService.projects().map { Environment.fromNamespace(it.metadata.name) }
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

        val namespace = dc.metadata.namespace
        val name = dc.metadata.name
        try {
            val violationRules = mutableSetOf<String>()
//            logger.info("finner applikasjon med navn={} i navnerom={}", name, namespace)
            val annotations = dc.metadata.annotations ?: emptyMap()

            //TODO: Wrong variable name
            val versionNumber = dc.status.latestVersion ?: 0
            val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

            val pods = getPods(dc).map(handleManagementInterface(managementPath, violationRules))
            val phase = getDeploymentPhase(name, namespace, versionNumber)

            val auroraIs = getAuroraImageStream(dc)

            val deployTag = dc.spec.triggers.find { it.type == "ImageChange" }
                    ?.imageChangeParams?.from?.name?.split(":")?.lastOrNull()

            val version = getAuroraVersion(dc, deployTag ?: "default")
                    ?: if (pods.isNotEmpty()) pods[0].info?.at("/auroraVersion")?.asText() else null
                            ?: auroraIs?.tag
            val affiliation = dc.metadata.labels["affiliation"]

            return ApplicationData(
                    applicationId = ApplicationId(name, Environment.fromNamespace(namespace, affiliation)),
                    name = name,
                    namespace = namespace,
                    deployTag = dc.metadata.labels["deployTag"] ?: deployTag ?: "",
                    booberDeployId = dc.metadata.labels["booberDeployId"],
                    affiliation = affiliation,
                    targetReplicas = dc.spec.replicas,
                    availableReplicas = dc.status.availableReplicas ?: 0,
                    deploymentPhase = phase,
                    managementPath = managementPath,
                    pods = pods,
                    imageStream = auroraIs,
                    sprocketDone = annotations["sprocket.sits.no-deployment-config.done"],
//                violationRules = violationRules,
                    auroraVersion = version ?: ""
            )
        } catch (e: Exception) {
            logger.error("Failed getting application name={}, namepsace={} message={}", name, namespace, e.message, e)
            throw e
        }
    }

    private fun handleManagementInterface(managementPath: String?, violationRules: MutableSet<String>): (PodDetails) -> PodDetails {
        return {
            val links = if (it.openShiftPodExcerpt.podIP.isBlank()) {
                emptyMap()
            } else if (managementPath == null) {
                violationRules.add("MANAGEMENT_PATH_MISSING")
                emptyMap()
            } else {
                try {
                    managmentApplicationService.findManagementEndpoints(it.openShiftPodExcerpt.podIP, managementPath).also {
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

            val info: JsonNode? = try {
                links?.let { managmentApplicationService.findResource(links["info"]) }
            } catch (e: HttpStatusCodeException) {
                violationRules.add("MANAGEMENT_INFO_ERROR_${e.statusCode}")
                null
            } catch (e: RestClientException) {
                violationRules.add("MANAGEMENT_INFO_ERROR_HTTP")
                null
            }

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

            it.copy(links = links, info = info) //, info = info, health = health)
        }
    }

    fun getAuroraVersion(dc: DeploymentConfig, deployTag: String): String? {
        val tag = openshiftService.imageStreamTag(dc.metadata.namespace, dc.metadata.name, deployTag)
        return tag?.auroraVersion
    }

    fun getAuroraImageStream(dc: DeploymentConfig): ImageDetails? {
        val trigger = dc.spec.triggers
                .filter { it.type == "ImageChange" }
                .map { it.imageChangeParams.from }
                .firstOrNull { it.kind == "ImageStreamTag" } ?: return null

        val triggerName = trigger.name

        //we need another way to find this.
        //need to find out if we have a development flow.
        val development = triggerName == "${dc.metadata.name}:latest"
        val deployTag = triggerName.split(":").lastOrNull()

        return openshiftService.imageStream(dc.metadata.namespace, dc.metadata.name)?.let {
            if (development) {
                val repoUrl = it.status.dockerImageRepository
                val (registryUrlPath, group, dockerName) = repoUrl.split("/")

                val registryUrl = "http://$registryUrlPath"
                val tag = "latest"
                return ImageDetails(
                        name = dockerName,
                        registryUrl = registryUrl,
                        group = group,
                        tag = tag,
                        env = null
                )
            }

            return it.spec.tags.filter { it.name == deployTag }
                    .map { it.from.name }
                    .firstOrNull()
                    ?.let {
                        try {
                            val (registryUrlPath, group, nameAndTag) = it.split("/")
                            val (dockerName, tag) = nameAndTag.split(":")
                            val registryUrl = "https://$registryUrlPath"
                            ImageDetails(
                                    name = dockerName,
                                    registryUrl = registryUrl,
                                    group = group,
                                    tag = tag,
                                    env = null
                            )
                        } catch (e: Exception) {
                            //TODO: Some urls might not be correct here, postgres straight from dockerHub ski-utv/ski2-test
                            logger.warn("Error splitting up deployTag $it")
                            null
                        }
                    }
        }
    }

    fun getPods(dc: DeploymentConfig): List<PodDetails> {
        val labelMap = dc.spec.selector.mapValues { it.value }
        return openshiftService.pods(dc.metadata.namespace, labelMap).map {
            val status = it.status.containerStatuses.first()
            PodDetails(
                    OpenShiftPodExcerpt(
                            name = it.metadata.name,
                            status = it.status.phase,
                            restartCount = status.restartCount,
                            ready = status.ready,
                            podIP = it.status.podIP ?: "",
                            deployment = it.metadata.labels["deployment"],
                            startTime = it.status.startTime
                    )
            )
        }
    }

    fun getDeploymentPhase(name: String, namespace: String, versionNumber: Long): String? {

        logger.debug("Get deployment phase name={}, namepace={}, number={}", name, namespace, versionNumber)
        if (versionNumber == 0L) {
            return null
        }

        val rcName = "$name-$versionNumber"
        //TODO: ReplicaSet vs ReplicationController
        return openshiftService.rc(namespace, rcName)?.let {
            it.metadata.annotations["openshift.io/deployment.phase"]
        }
    }
}