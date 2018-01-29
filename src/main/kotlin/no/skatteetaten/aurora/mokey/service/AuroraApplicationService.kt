package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Route
import io.prometheus.client.Gauge
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.AuroraImageStream
import no.skatteetaten.aurora.mokey.model.AuroraPod
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class AuroraApplicationService(val restTemplate: RestTemplate,
                               val openshiftService: OpenShiftService,
                               val dockerService: DockerService,
                               val mapper: ObjectMapper) {


    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationService::class.java)


    fun findApplication(namespace: String, dc: DeploymentConfig): AuroraApplication? {
        try {
            logger.info("finner applikasjon med navn={} i navnerom={}", dc.metadata.name, namespace)
            val annotations = dc.metadata.annotations ?: emptyMap()

            val versionNumber = dc.status.latestVersion ?: 0
            val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

            val name = dc.metadata.name
            val pods = getPods(namespace, name, managementPath, dc.spec.selector.mapValues { it.value })
            val phase = getDeploymentPhase(name, namespace, versionNumber)
            val route = getRouteUrls(namespace, name)


            val auroraIs = getAuroraImageStream(dc, name, namespace)

            return AuroraApplication(
                    name = name,
                    namespace = namespace,
                    affiliation = dc.metadata.labels["affiliation"],
                    targetReplicas = dc.spec.replicas,
                    availableReplicas = dc.status.availableReplicas ?: 0,
                    deploymentPhase = phase,
                    routeUrl = route,
                    managementPath = managementPath,
                    pods = pods,
                    imageStream = auroraIs,
                    sprocketDone = annotations["sprocket.sits.no-deployment-config.done"]
            )

        } catch (e: Exception) {
            logger.error("Failed getting application name={}, namepsace={} message={}", dc.metadata.name, namespace, e.message, e)
            return null
        }
    }

    fun getAuroraImageStream(dc: DeploymentConfig, name: String, namespace: String): AuroraImageStream? {
        val trigger = dc.spec.triggers
                .filter { it.type == "ImageChange" }
                .map { it.imageChangeParams.from }
                .firstOrNull { it.kind != "ImageStreamTag" }


        if (trigger == null) {
            return null
        }
        val triggerName = trigger.name

        //need to find out if we have a development flow.
        val development = triggerName == "$name:latest"


        val deployTag = triggerName.split(":")[1]

        return openshiftService.imageStream(namespace, name)?.let {
            if (development) {
                val repoUrl = it.status.dockerImageRepository
                val (registryUrlPath, group, dockerName) = repoUrl.split("/")

                val registryUrl = "http://$registryUrlPath"
                val tag = "latest"
                val token = openshiftService.openShiftClient.configuration.oauthToken
                val env = dockerService.getEnv(registryUrl, "$group/$name", tag, token)
                return AuroraImageStream(deployTag, registryUrl, group, dockerName, tag, env)
            }

            return it.spec.tags.filter {
                it.name == deployTag
            }.map {
                it.from.name
            }.firstOrNull()?.let {

                val (registryUrlPath, group, nameAndTag) = it.split("/")
                val (dockerName, tag) = nameAndTag.split(":")
                val registryUrl = "https://$registryUrlPath"
                val env = dockerService.getEnv(registryUrl, "$group/$dockerName", tag)
                AuroraImageStream(deployTag, registryUrl, group, dockerName, tag, env)
            }
        }
    }

    fun getRouteUrls(namespace: String, name: String): String? {
        return try {
            openshiftService.route(namespace, name)?.let {
                getURL(it)
            }
        } catch (e: Exception) {
            logger.debug("Route name={}, namespace={} not found", name, namespace)
            null
        }
    }

    fun getURL(route: Route): String {

        val spec = route.spec


        val scheme = if (spec.tls != null) "https" else "http"

        val path = if (!spec.path.isNullOrBlank()) {
            val p = spec.path
            if (!p.startsWith("/")) {
                "/$p"
            } else {
                p
            }
        } else {
            ""
        }

        val host = spec.host
        return "$scheme://$host$path"
    }

    fun getPods(namespace: String, name: String, managementPath: String?, labelMap: Map<String, String>): List<AuroraPod> {

        logger.debug("find pods namespace={} lables={}", namespace, labelMap)
        return openshiftService.pods(namespace, labelMap).map {
            val containerStatus = it.status.containerStatuses.first()
            val ip = it.status.podIP


            val links = if (ip == null || managementPath == null) {
                emptyMap()
            } else {
                findManagementEndpoints(ip, managementPath)
            }

            val info = findResource(links["info"], namespace, name)
            val health = findResource(links["health"], namespace, name)

            AuroraPod(
                    name = it.metadata.name,
                    status = it.status.phase,
                    restartCount = containerStatus.restartCount,
                    podIP = ip,
                    isReady = containerStatus.ready,
                    deployment = it.metadata.labels["deployment"],
                    links = links,
                    info = info,
                    health = health,
                    startTime = it.status.startTime
            )
        }
    }

    private fun findResource(url: String?, namespace: String, name: String): JsonNode? {
        if (url == null) {
            return null
        }
        return try {
            logger.debug("Find resource with url={}", url)
            restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: HttpStatusCodeException) {
            //TODO check error code 500
            return mapper.readTree(e.responseBodyAsByteArray)
        } catch (e: RestClientException) {
            logger.warn("Error getting resource for namespace={} name={} url={}", namespace, name, url)
            null
        }
    }

    fun getDeploymentPhase(name: String, namespace: String, versionNumber: Long): String? {

        logger.debug("Get deployment phase name={}, namepace={}, number={}", name, namespace, versionNumber)
        if (versionNumber == 0L) {
            return null
        }

        val rcName = "$name-$versionNumber"
        return openshiftService.rc(namespace, rcName)?.let {
            it.metadata.annotations["openshift.io/deployment.phase"]
        }
    }

    private fun findManagementEndpoints(podIP: String, managementPath: String): Map<String, String> {

        logger.debug("Find management endpoints ip={}, path={}", podIP, managementPath)
        val managementUrl = "http://${podIP}$managementPath"

        val managementEndpoints = try {
            restTemplate.getForObject(managementUrl, JsonNode::class.java)
        } catch (e: RestClientException) {
            return emptyMap()
        }

        if (!managementEndpoints.has("_links")) {
            logger.debug("Management endpoint does not have links at url={}", managementUrl)
            return emptyMap()
        }
        return managementEndpoints["_links"].asMap().mapValues { it.value["href"].asText() }

    }

    fun calculateHealth(app: AuroraApplication, gauge: Gauge) {

        //denne må bli gjort for hver app
        val status = AuroraStatusCalculator.calculateStatus(app.pods, app.deploymentPhase, app.targetReplicas, app.availableReplicas)

        val auroraVersion = app.pods[0].info?.at("auroraVersion")?.asText() ?: "Unknown"
        gauge.labels(app.name, app.namespace, auroraVersion, status.comment)
                .set(status.level.level.toDouble())
    }
}