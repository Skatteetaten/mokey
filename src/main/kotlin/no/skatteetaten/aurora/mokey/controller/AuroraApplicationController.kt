package no.skatteetaten.aurora.mokey.controller

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.openshift.client.DefaultOpenShiftClient
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import no.skatteetaten.aurora.mokey.facade.AuroraApplicationFacade
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.Response
import no.skatteetaten.aurora.mokey.service.NoAccessException
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.StopWatch
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern


@RestController
@RequestMapping("/aurora/application")
class AuroraApplicationController(
        val openShiftService: OpenShiftService,
        val facade: AuroraApplicationFacade) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationController::class.java)

    val cache = ConcurrentHashMap<String, AuroraApplication>()
    var cachePopulated = false

    private fun checkPermissions(authorization: String, namespace: String) {
        val headerPattern: Pattern = Pattern.compile("Bearer\\s+(.*)", Pattern.CASE_INSENSITIVE)
        val matcher = headerPattern.matcher(authorization)
        if (!matcher.find()) {
            throw NoAccessException("Unexpected Authorization header format")
        }
        val token = matcher.group(1)
        try {
            logger.debug("lager klient")
            val client = DefaultOpenShiftClient(ConfigBuilder().withOauthToken(token).build())
            logger.debug("henter prosjekt")
            client.projects().withName(namespace).getOrNull()
        } catch (e: Exception) {
            throw NoAccessException("An unexpected error occurred while getting OpenShift user")
        } ?: throw NoAccessException("No access")

        logger.debug("fant prosjekt")
    }

    //@Scheduled(fixedRate = 300000, initialDelay = 360)
    @GetMapping("/process/{namespace}")
    fun list(@PathVariable namespace: String) {

        val watch = StopWatch()
        watch.start()
        logger.debug("Start scheduled task")

        val allKeys = cache.keys().toList()
        val newKeys = mutableListOf<String>()


        val projects = openShiftService.projects().map { it.metadata.name }
        //val projects = listOf(namespace)

        logger.info("Fetched {} projects", projects.size)

        //CONFIG
        val mtContext = newFixedThreadPoolContext(6, "mookeyPool")
        runBlocking(mtContext) {
            projects.flatMap { namespace ->
                logger.debug("Find all applications in namespace={}", namespace)
                openShiftService.deploymentConfigs(namespace).map { dc ->
                    launch(mtContext) {
                        val app = facade.findApplication(namespace, dc)

                        app?.let {
                            val appKey = "${it.namespace}/${it.name}"
                            logger.info("New cache for {}", appKey)
                            cache.put(appKey, it)
                            synchronized(newKeys) {
                                newKeys.add(appKey)
                            }
                        }

                        if (app == null) {
                            logger.error("APP IS NULL! namespace={}, name={}", dc.metadata.namespace, dc.metadata.name)
                        }
                    }
                }
            }.forEach { it.join() }
            val deleteKeys = allKeys - newKeys
            deleteKeys.forEach {
                logger.info("Remove application since it does not exist anymore {}", it)
                cache.remove(it)
            }

            cachePopulated = true
            watch.stop()
            val keys = cache.keys().toList()
            logger.debug("cache keys={}", keys)
            logger.info("number of apps={} time={}", keys.size, watch.totalTimeSeconds)
        }


    }

    @GetMapping("/namespace/{namespace}/application/{application}")
    fun get(@PathVariable namespace: String, @PathVariable application: String,
            @RequestHeader(value = "Authorization") authorization: String
    ): Response {
        checkPermissions(authorization, namespace)

        logger.debug("finner applikasjon")
        val appKey = "$namespace/$application"
        return cache.get(appKey)?.let {
            Response(items = listOf(it))
        } ?: Response(success = false, message = "Does not exist", items = emptyList())
    }
}


