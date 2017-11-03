package no.skatteetaten.aurora.mokey.controller

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.openshift.client.DefaultOpenShiftClient
import io.fabric8.openshift.client.OpenShiftClient
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import no.skatteetaten.aurora.mokey.facade.AuroraApplicationFacade
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.Response
import no.skatteetaten.aurora.mokey.service.NoAccessException
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
class AuroraApplicationController(val facade: AuroraApplicationFacade, val client: OpenShiftClient) {

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

        //this model is very simple, we should probably look at evicting old records as well
        //val projects = client.projects().list().items.map { it.metadata.name }
        val projects = listOf(namespace)

        logger.debug("Fetched {} projects", projects.size)


        val mtContext = newFixedThreadPoolContext(4, "mookeyPool")
        runBlocking(mtContext) {
            val request = launch(mtContext) {
                projects.forEach { namespace ->
                    logger.debug("Find all applications in namespace={}", namespace)
                    val applications = async(mtContext) {
                        facade.findApplications(namespace).map {
                            namespace to it
                        }
                    }
                    applications.await().forEach {
                        launch(mtContext) {
                            val namespace = it.first
                            val name = it.second
                            val appKey = "$namespace/$name"
                            logger.debug("process application in namespace={} name={}", namespace, name)
                            try {
                                val app = facade.findApplication(namespace, name)
                                if (app == null) {
                                    logger.trace("Remove application {}", appKey)
                                    cache.remove(appKey)
                                } else {
                                    logger.trace("New cache for {}", appKey)
                                    cache.put(appKey, app)
                                }
                                newKeys.add(appKey)
                            } catch (e: Exception) {
                                logger.trace("Error when finding application {}", appKey)
                            }
                        }
                    }
                }
            }
            request.join()
        }

        val deleteKeys = allKeys - newKeys
        deleteKeys.forEach {
            logger.debug("Remove application since it does not exist anymore {}", it)
            cache.remove(it)
        }



        cachePopulated = true
        watch.stop()
        logger.debug("DONE! took {}", watch.totalTimeSeconds);

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


