package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

@Service
class AuroraApplicationCacheService(val openShiftService: OpenShiftService, val applicationService: AuroraApplicationService) {
    val cache = ConcurrentHashMap<String, AuroraApplication>()
    var cachePopulated = false


    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationCacheService::class.java)
    val mtContext = newFixedThreadPoolContext(6, "mookeyPool")

    fun load(projects: List<String>) {

        val watch = StopWatch()
        watch.start()
        val allKeys = cache.keys().toList()
        val newKeys = mutableListOf<String>()
        runBlocking(mtContext) {
            projects.flatMap { namespace ->
                logger.debug("Find all applications in namespace={}", namespace)
                openShiftService.deploymentConfigs(namespace).map { dc ->
                    launch(mtContext) {
                        val app = applicationService.handleApplication(namespace, dc)
                        //TODO: Create violationTags
                        //TODO: Create status

                        app?.let {
                            val appKey = "${it.namespace}/${it.name}"
                            cache.put(appKey, it)
                            synchronized(newKeys) {
                                newKeys.add(appKey)
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
            val violations = cache.values.filter { it.violationRules.isNotEmpty() }
                    .flatMap {
                        val aid = "${it.namespace}/${it.name}"
                        it.violationRules.map { it to aid }
                    }.groupBy(keySelector = { it.first }, valueTransform = { it.second })

            violations.map {
                logger.info("Violation rule ${it.key} applications=${it.value}")
            }
        }

    }

    fun get(key: String): AuroraApplication? {
        return cache[key]
    }
}