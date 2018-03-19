package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.Environment
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

@Service
class CachedApplicationDataService(val openShiftApplicationDataService: OpenShiftApplicationDataService) {

    val cache = ConcurrentHashMap<String, ApplicationData>()

    val logger: Logger = LoggerFactory.getLogger(CachedApplicationDataService::class.java)

    // @Scheduled(fixedRate = 300000, initialDelay = 360)
    fun refreshCache(environments: List<Environment>? = null) {

        fun withStopWatch(block: () -> Unit): StopWatch {
            return StopWatch().also {
                it.start()
                block()
                it.stop()
            }
        }

        val allKeys = cache.keys().toList()
        val newKeys = mutableListOf<String>()

        val time = withStopWatch {
            val applications = if (environments != null) {
                openShiftApplicationDataService.findAllApplications(environments)
            } else {
                openShiftApplicationDataService.findAllApplications()
            }

            applications.forEach {
                cache[it.id] = it
                synchronized(newKeys) {
                    newKeys.add(it.id)
                }
            }
        }

        val deleteKeys = allKeys - newKeys
        deleteKeys.forEach {
            logger.info("Remove application since it does not exist anymore {}", it)
            cache.remove(it)
        }
        val keys = cache.keys().toList()
        logger.debug("cache keys={}", keys)
        logger.info("number of apps={} time={}", keys.size, time.totalTimeSeconds)
    }

    fun getAffiliations(): List<String> {
        return cache.mapNotNull { it.value.affiliation }
                .filter(String::isNotBlank)
                .distinct()
    }

    fun findApplicationDataById(id: String): ApplicationData? {
        return cache[id]
    }

    fun findAllApplicationDataByAffiliations(affiliation: List<String>): List<ApplicationData> {
        return cache.filter { affiliation.contains(it.value.affiliation) }.map { it.value }
    }
}