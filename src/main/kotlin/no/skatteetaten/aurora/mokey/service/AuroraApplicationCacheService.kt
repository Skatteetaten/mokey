package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.controller.ApplicationId
import no.skatteetaten.aurora.mokey.controller.Environment
import no.skatteetaten.aurora.mokey.model.ApplicationData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

@Service
class AuroraApplicationCacheService(val openShiftService: OpenShiftService, val applicationService: AuroraApplicationService) {
    val cache = ConcurrentHashMap<ApplicationId, ApplicationData>()
    var cachePopulated = false

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationCacheService::class.java)
    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    fun load(projects: List<String>) {

        val watch = StopWatch()
        watch.start()
        val allKeys = cache.keys().toList()
        val newKeys = mutableListOf<ApplicationId>()
        runBlocking(mtContext) {
            projects.flatMap { namespace ->
                logger.debug("Find all applications in namespace={}", namespace)
                openShiftService.deploymentConfigs(namespace).map { dc ->
                    launch(mtContext) {
                        val appId = ApplicationId(dc.metadata.name, Environment.fromNamespace(namespace))
                        val app = applicationService.handleApplication(dc)

                        app?.let {
                            cache[appId] = app
                            synchronized(newKeys) {
                                newKeys.add(appId)
                            }
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

    fun get(key: ApplicationId): ApplicationData? {
        return cache[key]
    }

    fun getAffiliations(): List<String> {
        return cache.map { it.value.affiliation }
            .filter(String::isNotBlank)
            .distinct()
    }

    fun getAppsInAffiliations(affiliation: List<String>): List<ApplicationData> {
        return cache.filter {
            affiliation.contains(it.value.affiliation)
        }.map{
                it.value
        }

    }
}