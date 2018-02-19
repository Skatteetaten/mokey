package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class AuroraApplicationService(
    val meterRegistry: MeterRegistry,
    val openShiftApplicationService: OpenShiftApplicationService,
    val dockerService: DockerService,
    val managmentApplicationService: ManagmentApplicationService
) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationService::class.java)

    fun handleApplication(namespace: String, dc: DeploymentConfig): AuroraApplication? {

        return findApplication(namespace, dc)?.also { app ->

            val status = AuroraStatusCalculator.calculateStatus(app)
            val version = app.imageStream?.env?.get("AURORA_VERSION")
                ?: app.pods[0].info?.at("/auroraVersion")?.asText()
                ?: app.imageStream?.tag ?: "Unknown"

            //TODO: Burde vi hatt en annen metrikk for apper som ikke er deployet med Boober?
            val commonTags = listOf(
                Tag.of("aurora_version", version),
                Tag.of("aurora_namespace", app.namespace),
                Tag.of("aurora_affiliation", app.affiliation),
                Tag.of("aurora_name", app.name),
                Tag.of("aurora_deploy_tag", app.deployTag ?: "Unknown")
            )

            app.violationRules.forEach {
                meterRegistry.counter("aurora_violation_trend", commonTags + Tag.of("violation", it))
            }

            //  meterRegistry.counter("aurora_status_trend", commonTags + Tag.of("status", status.level.toString())).increment()
            meterRegistry.gauge("aurora_status", commonTags, status.level.level)

        }
    }

    fun findApplication(namespace: String, dc: DeploymentConfig): AuroraApplication? {
        try {
            val violationRules = mutableListOf<String>()
            logger.info("finner applikasjon med navn={} i navnerom={}", dc.metadata.name, namespace)
            val annotations = dc.metadata.annotations ?: emptyMap()

            val versionNumber = dc.status.latestVersion ?: 0
            val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

            val deployTag = dc.metadata.labels["deployTag"]
            val booberDeployId = dc.metadata.labels["booberDeployId"]
            val name = dc.metadata.name
            val pods = openShiftApplicationService.getPods(
                namespace,
                name,
                managementPath,
                dc.spec.selector.mapValues { it.value }).map {
                val links = if (it.podIP == null || managementPath == null) {
                    emptyMap()
                } else {
                    managmentApplicationService.findManagementEndpoints(it.podIP, managementPath).also {
                        if (it.isEmpty()) {
                            violationRules.add("NOT_VALID_MANAGEMENT_ENDPOINT")
                        }
                    }
                }

                val info = managmentApplicationService.findResource(links["info"], namespace, name)
                val health = managmentApplicationService.findResource(links["health"], namespace, name)
                it.copy(links = links, info = info, health = health)
            }

            val phase = openShiftApplicationService.getDeploymentPhase(name, namespace, versionNumber)
            val route = openShiftApplicationService.getRouteUrls(namespace, name)

            val auroraIs = openShiftApplicationService.getAuroraImageStream(dc, name, namespace)?.let {
                val token = if (it.localImage) openShiftApplicationService.token else null
                val env = dockerService.getEnv(it.registryUrl, "${it.group}/${it.name}", it.tag, token)
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
            logger.error(
                "Failed getting application name={}, namepsace={} message={}",
                dc.metadata.name,
                namespace,
                e.message,
                e
            )
            return null
        }
    }
}