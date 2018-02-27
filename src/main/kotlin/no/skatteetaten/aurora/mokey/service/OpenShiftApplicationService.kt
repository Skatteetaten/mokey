package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfig
import io.fabric8.openshift.api.model.Route
import no.skatteetaten.aurora.mokey.extensions.ensureStartWith
import no.skatteetaten.aurora.mokey.model.AuroraImageStream
import no.skatteetaten.aurora.mokey.model.AuroraPod
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OpenShiftApplicationService(val openshiftService: OpenShiftService) {

    val logger: Logger = LoggerFactory.getLogger(OpenShiftApplicationService::class.java)


    val token = openshiftService.openShiftClient.configuration.oauthToken

    fun getAuroraImageStream(dc: DeploymentConfig, name: String, namespace: String): AuroraImageStream? {
        val trigger = dc.spec.triggers
                .filter { it.type == "ImageChange" }
                .map { it.imageChangeParams.from }
                .firstOrNull { it.kind == "ImageStreamTag" }


        if (trigger == null) {
            return null
        }
        val triggerName = trigger.name

        //we need another way to find this.
        //need to find out if we have a development flow.
        val development = triggerName == "$name:latest"


        val deployTag = triggerName.split(":")[1]

        return openshiftService.imageStream(namespace, name)?.let {
            if (development) {
                val repoUrl = it.status.dockerImageRepository
                val (registryUrlPath, group, dockerName) = repoUrl.split("/")

                val registryUrl = "http://$registryUrlPath"
                val tag = "latest"
                return AuroraImageStream(registryUrl, group, dockerName, tag, true)
            }

            return it.spec.tags.filter { it.name == deployTag }
                    .map { it.from.name }
                    .firstOrNull()
                    ?.let {
                        try {
                            val (registryUrlPath, group, nameAndTag) = it.split("/")
                            val (dockerName, tag) = nameAndTag.split(":")
                            val registryUrl = "https://$registryUrlPath"
                            AuroraImageStream(registryUrl, group, dockerName, tag)
                        } catch (e: Exception) {
                            //TODO: Some urls might not be correct here, postgres straight from dockerHub ski-utv/ski2-test
                            logger.warn("Error splitting up deployTag ${it}")
                            null
                        }
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
            spec.path.ensureStartWith("/")
        } else {
            ""
        }
        val host = spec.host
        return "$scheme://$host$path"
    }

    fun getPods(namespace: String, name: String, managementPath: String?, labelMap: Map<String, String>): List<AuroraPod> {

        logger.debug("find pods namespace={} lables={}", namespace, labelMap)
        return openshiftService.pods(namespace, labelMap).map {
            val status = it.status.containerStatuses.first()
            AuroraPod(
                    name = it.metadata.name,
                    status = it.status.phase,
                    restartCount = status.restartCount,
                    podIP = it.status.podIP,
                    ready = status.ready,
                    deployment = it.metadata.labels["deployment"],
                    startTime = it.status.startTime
            )
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

}