package no.skatteetaten.aurora.mokey.facade

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import io.prometheus.client.Gauge
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

import no.skatteetaten.aurora.mokey.extensions.asMap
import no.skatteetaten.aurora.mokey.extensions.asOptionalString
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.AuroraImageStream
import no.skatteetaten.aurora.mokey.model.AuroraPod
import no.skatteetaten.aurora.mokey.service.AuroraStatusCalculator
import no.skatteetaten.aurora.mokey.service.DockerService
import no.skatteetaten.aurora.mokey.service.openshift.OpenShiftResourceClient

@Service
class AuroraApplicationFacade(val client: OpenShiftResourceClient,
                              val restTemplate: RestTemplate,
                              val dockerService: DockerService, val mapper: ObjectMapper) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationFacade::class.java)

    fun findApplication(namespace: String, name: String): AuroraApplication? {

        //urlo to dc
        val urlToDc=""
        val dc = client.get(urlToDc)?.body

        return dc?.let {
            val status = it["status"]

            val metadata = it["metadata"]
            val spec = it["spec"]
            val labels = metadata["labels"]
            val annotations = metadata["annotations"]

            val versionNumber = status["latestVersion"].asOptionalString()?.toInt() ?: 0
            val managementPath = annotations["console.skatteetaten.no/management-path"].asOptionalString()

            val pods = getPods(namespace, managementPath, spec["selector"].asMap().mapValues { it.value.textValue() })
            val phase = getDeploymentPhase(name, namespace, versionNumber)
            val route = getRouteUrls(namespace, name)


            //val auroraIs = getAuroraImageStream(it, name, namespace)

            AuroraApplication(
                    name = name,
                    namespace = namespace,
                    affiliation = labels["affiliation"].textValue(),
                    targetReplicas = spec["replicas"].intValue(),
                    availableReplicas = status["availableReplicas"].asOptionalString()?.toInt() ?: 0,
                    deploymentPhase = phase,
                    routeUrl = route,
                    managementPath = managementPath,
                    pods = pods,
                    //  imageStream = auroraIs,
                    sprocketDone = annotations["sprocket.sits.no-deployment-config.done"].asOptionalString()
            )


        }
        //in paralell
        //get status.latestVersion and fetch rc with that name
        //get all pods for aid
        //get route urls
        //get info from prometheus
        //get management endpoints from applications if managementInterface is present

        //if prometheus status reason is HEALTH_CHECK_FAILED fetch health endpoints from pods

    }

    fun getAuroraImageStream(dc: JsonNode, name: String, namespace: String): AuroraImageStream? {
        //todo search for it instead of just use 0

        val triggerFrom = dc.at("/spec/triggers/0/imageChangeParams/from")
        val kind = triggerFrom["kind"].asOptionalString()
        if (kind != "ImageStreamTag") {
            return null
        }

        val deployTag = triggerFrom["name"].asText().split(":")[1]

        val imageSTreamUrl=""
        return client.get(imageSTreamUrl)?.body?.let {
            val tags = it.at("/spec/tags") as ArrayNode
            val dockerUrl = tags.filter {
                it["name"].asText() == deployTag
            }.map {
                it["from"]["name"].asText()
            }.first()

            val (registryUrl, group, nameAndTag) = dockerUrl.split("/")
            val (name, tag) = nameAndTag.split(":")

            AuroraImageStream(deployTag, registryUrl, group, name, tag)
        }


    }

    fun getRouteUrls(namespace: String, name: String): String? {
        val urlToRoute=""
        return client.get(urlToRoute)?.body?.let {
            getURL(it)
        }
    }

    fun getURL(routeJson: JsonNode): String {

        val spec = routeJson["spec"]
        val scheme = if (spec.has("tls")) "https" else "http"

        val path = if (spec.has("path")) {
            val p = spec["path"].textValue()
            if (!p.startsWith("/")) {
                "/$p"
            } else {
                p
            }
        } else {
            ""
        }

        val host = spec["host"].asText()
        return "$scheme://$host$path"
    }

    fun getPods(namespace: String, managementPath: String?, labels: Map<String, String>): List<AuroraPod> {
        val podUrl=""
        val res = client.list(podUrl, labels)

        return res.map {
            val status = it["status"]
            val containerStatus = status["containerStatuses"].get(0)
            val metadata = it["metadata"]
            val labels = metadata["labels"]

            val ip = status["podIP"].asText()

            //we should be able to cache this
            val links: Map<String, String> = managementPath?.let {
                findManagementEndpoints(ip, it)
            } ?: emptyMap()


            val info = findResource(links["info"])
            val health = findResource(links["health"])

            AuroraPod(
                    name = metadata["name"].asText(),
                    status = status["phase"].asText(),
                    restartCount = containerStatus["restartCount"].asInt(),
                    podIP = ip,
                    isReady = containerStatus["ready"].asBoolean(false),
                    deployment = labels["deployment"].asText(),
                    links = links,
                    info = info,
                    health = health,
                    startTime = status["startTime"].asText()
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
            return null
        }
    }

    fun getDeploymentPhase(name: String, namespace: String, versionNumber: Int): String? {

        if (versionNumber == 0) {
            return null
        }

        val rcUrl=""
        val rc = client.get(rcUrl)
        return rc?.body?.at("/metadata/annotations")?.get("openshift.io/deployment.phase")?.asText()

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