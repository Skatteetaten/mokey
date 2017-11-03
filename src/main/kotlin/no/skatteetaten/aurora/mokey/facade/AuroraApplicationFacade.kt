package no.skatteetaten.aurora.mokey.facade

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Route
import io.fabric8.openshift.client.OpenShiftClient
import io.prometheus.client.Gauge
import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.AuroraImageStream
import no.skatteetaten.aurora.mokey.model.AuroraPod
import no.skatteetaten.aurora.mokey.service.AuroraStatusCalculator
import no.skatteetaten.aurora.mokey.service.DockerService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@Service
class AuroraApplicationFacade(val restTemplate: RestTemplate,
                              val dockerService: DockerService,
                              val openshiftClient: OpenShiftClient,
                              val mapper: ObjectMapper) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationFacade::class.java)

    fun findApplications(namespace: String): List<String> {
        return openshiftClient.deploymentConfigs().inNamespace(namespace).list().items.map {
            it.metadata.name
        }
    }

    fun findApplication(namespace: String, name: String): AuroraApplication? {


        logger.debug("finner applikasjon med navn={} i navnerom={}", name, namespace)
        val dc = openshiftClient.deploymentConfigs()
                .inNamespace(namespace)
                .withName(name).getOrNull()

        return dc?.let {
            val status = it.status
            val metadata = it.metadata
            val spec = it.spec
            val labels = metadata.labels
            val annotations = metadata.annotations

            val versionNumber = status.latestVersion ?: 0
            val managementPath: String? = annotations["console.skatteetaten.no/management-path"]

            val pods = getPods(namespace, managementPath, spec.selector.mapValues { it.value })
            val phase = getDeploymentPhase(name, namespace, versionNumber)
            val route = getRouteUrls(namespace, name)


            val auroraIs = getAuroraImageStream(it, name, namespace)

            AuroraApplication(
                    name = name,
                    namespace = namespace,
                    affiliation = labels["affiliation"],
                    targetReplicas = spec.replicas,
                    availableReplicas = status.availableReplicas ?: 0,
                    deploymentPhase = phase,
                    routeUrl = route,
                    managementPath = managementPath,
                    pods = pods,
                    imageStream = auroraIs,
                    sprocketDone = annotations["sprocket.sits.no-deployment-config.done"]
            )


        }
    }

    fun getAuroraImageStream(dc: DeploymentConfig, name: String, namespace: String): AuroraImageStream? {
        val triggerFrom = dc.spec.triggers.first().imageChangeParams.from
        val kind = triggerFrom.kind
        if (kind != "ImageStreamTag") {
            return null
        }

        val triggerName = triggerFrom.name

        //need to find out if we have a development flow.
        val development = triggerName == "$name:latest"


        val deployTag = triggerName.split(":")[1]

        return openshiftClient.imageStreams().inNamespace(namespace).withName(name).getOrNull()?.let {
            if (development) {
                it.spec
                val repoUrl = it.status.dockerImageRepository
                val (registryUrl, group, dockerName) = repoUrl.split("/")

                val tag = "latest"
                val token = openshiftClient.configuration.oauthToken
                val env = dockerService.getEnv(registryUrl, "$group/$name", tag, token)
                return AuroraImageStream(deployTag, registryUrl, group, dockerName, tag, env)
            }

            return it.spec.tags.filter {
                it.name == deployTag
            }.map {
                it.from.name
            }.firstOrNull()?.let {

                val (registryUrl, group, nameAndTag) = it.split("/")
                val (dockerName, tag) = nameAndTag.split(":")
                val env = dockerService.getEnv(registryUrl, "$group/$dockerName", tag)
                AuroraImageStream(deployTag, registryUrl, group, dockerName, tag, env)
            }
        }
    }

    fun getRouteUrls(namespace: String, name: String): String? {
        return openshiftClient.routes().inNamespace(namespace).withName(name).getOrNull()?.let {
            getURL(it)
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

    fun getPods(namespace: String, managementPath: String?, labelMap: Map<String, String>): List<AuroraPod> {

        val pods = openshiftClient.pods().inNamespace(namespace).withLabels(labelMap).list()

        return pods.items.map {
            val status = it.status
            val containerStatus = status.containerStatuses.first()
            val metadata = it.metadata
            val labels = metadata.labels
            val ip = status.podIP

            //we should be able to cache this
            val links: Map<String, String> = managementPath?.let {
                findManagementEndpoints(ip, it)
            } ?: emptyMap()


            val info = findResource(links["info"])
            val health = findResource(links["health"])

            AuroraPod(
                    name = metadata.name,
                    status = status.phase,
                    restartCount = containerStatus.restartCount,
                    podIP = ip,
                    isReady = containerStatus.ready,
                    deployment = labels["deployment"],
                    links = links,
                    info = info,
                    health = health,
                    startTime = status.startTime
            )
        }
    }

    private fun findResource(url: String?): JsonNode? {
        if (url == null) {
            return null
        }
        try {
            return restTemplate.getForObject(url, JsonNode::class.java)
        } catch (e: RestClientException) {
            //TODO: error handling
            return null
        }
    }

    fun getDeploymentPhase(name: String, namespace: String, versionNumber: Long): String? {

        if (versionNumber == 0L) {
            return null
        }

        val rcName = "$name-$versionNumber"
        return openshiftClient.replicationControllers().inNamespace(namespace).withName(rcName).getOrNull()?.let {
            it.metadata.annotations["openshift.io/deployment.phase"]
        }
    }

    private fun findManagementEndpoints(podIP: String, managementPath: String): Map<String, String> {

        val managementUrl = "http://${podIP}$managementPath"

        val managementEndpoints = try {
            restTemplate.getForObject(managementUrl, JsonNode::class.java)
        } catch (e: RestClientException) {
            return emptyMap()
        }

        if (!managementEndpoints.has("_links")) {
            logger.warn("Management endpoint does not have links at url={}", managementUrl)
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