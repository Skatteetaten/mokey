package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.openshift.api.model.DeploymentConfig
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException

@Service
class AuroraApplicationService(val meterRegistry: MeterRegistry,
                               val openShiftApplicationService: OpenShiftApplicationService,
                               val dockerService: DockerService,
                               val managmentApplicationService: ManagmentApplicationService,
                               val mapper: ObjectMapper) {


    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationService::class.java)


    fun handleApplication(namespace: String, dc: DeploymentConfig): AuroraApplication? {

        return findApplication(namespace, dc)?.also { app ->

            val status = AuroraStatusCalculator.calculateStatus(app)
            val version = app.imageStream?.env?.get("AURORA_VERSION")
                    ?: if (app.pods.isNotEmpty()) app.pods[0].info?.at("/auroraVersion")?.asText() else null
                            ?: app.imageStream?.tag

            //TODO: Burde vi hatt en annen metrikk for apper som ikke er deployet med Boober?
            val commonTags = listOf(
                    Tag.of("aurora_version", version ?: "Unknown"),
                    Tag.of("aurora_namespace", app.namespace),
                    Tag.of("aurora_name", app.name),
                    Tag.of("aurora_affiliation", app.affiliation),
                    Tag.of("version_strategy", app.deployTag ?: "Unknown"))

            app.violationRules.forEach {
                meterRegistry.counter("aurora_violation", commonTags + Tag.of("violation", it))
            }

            meterRegistry.gauge("aurora_status", commonTags, status.level.level)


        }
    }


    fun findApplication(namespace: String, dc: DeploymentConfig): AuroraApplication? {
        try {
            val violationRules = mutableSetOf<String>()
            logger.info("finner applikasjon med navn={} i navnerom={}", dc.metadata.name, namespace)
            val annotations = dc.metadata.annotations ?: emptyMap()

            //TODO: Wrong variable name
            val versionNumber = dc.status.latestVersion ?: 0
            val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

            val deployTag = dc.metadata.labels["deployTag"]
            val booberDeployId = dc.metadata.labels["booberDeployId"]
            val name = dc.metadata.name
            val pods = openShiftApplicationService.getPods(namespace, name, managementPath, dc.spec.selector.mapValues { it.value }).map {
                val links = if (it.podIP == null) {
                    emptyMap()
                } else if (managementPath == null) {
                    violationRules.add("MANAGEMENT_PATH_MISSING")
                    emptyMap()
                } else {
                    try {
                        managmentApplicationService.findManagementEndpoints(it.podIP, managementPath).also {
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

                val info = try {
                    links?.let { managmentApplicationService.findResource(links["info"], namespace, name) }
                } catch (e: HttpStatusCodeException) {
                    violationRules.add("MANAGEMENT_INFO_ERROR_${e.statusCode}")
                    null
                } catch (e: RestClientException) {
                    violationRules.add("MANAGEMENT_INFO_ERROR_HTTP")
                    null
                }

                val health = try {
                    links?.let { managmentApplicationService.findResource(links["health"], namespace, name) }
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

                it.copy(links = links, info = info, health = health)
            }

            val phase = openShiftApplicationService.getDeploymentPhase(name, namespace, versionNumber)

            //TODO: Will only fetch main Route. Do we need labels here? label app=name
            val route = openShiftApplicationService.getRouteUrls(namespace, name)

            val auroraIs = openShiftApplicationService.getAuroraImageStream(dc, name, namespace)?.let {
                val token = if (it.localImage) openShiftApplicationService.token else null

                val env = try {
                    val env = dockerService.getEnv(it.registryUrl, "${it.group}/${it.name}", it.tag, token)
                    if (env == null || env.isEmpty()) {
                        violationRules.add("DOCKER_EMPTY_ENV_ERROR")
                    }
                    env
                } catch (e: HttpStatusCodeException) {
                    violationRules.add("DOCKER_ENDPOINT_ERROR_${e.statusCode}")
                    null
                } catch (e: Exception) {
                    violationRules.add("DOCKER_ENDPOINT_ERROR_HTTP")
                    //       logger.warn("Error getting management endpoints", e)
                    null
                }
                it.copy(env = env)
            }

            return AuroraApplication(
                    name = name,
                    namespace = namespace,
                    deployTag = deployTag,
                    booberDeployId = booberDeployId,
                    affiliation = dc.metadata.labels["affiliation"],
                    targetReplicas = dc.spec.replicas,
                    availableReplicas = dc.status.availableReplicas ?: 0,
                    deploymentPhase = phase,
                    routeUrl = route,
                    managementPath = managementPath,
                    pods = pods,
                    imageStream = auroraIs,
                    sprocketDone = annotations["sprocket.sits.no-deployment-config.done"],
                    violationRules = violationRules
            )

        } catch (e: Exception) {
            logger.error("Failed getting application name={}, namepsace={} message={}", dc.metadata.name, namespace, e.message, e)
            return null
        }
    }
}