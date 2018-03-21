package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

@Service
class ApplicationDataServiceCacheDecorator(val applicationDataService: ApplicationDataServiceOpenShift) : ApplicationDataService {

    val cache = ConcurrentHashMap<String, ApplicationData>()

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceCacheDecorator::class.java)

    override fun findAllAffiliations(): List<String> {
        return cache.mapNotNull { it.value.affiliation }
                .filter(String::isNotBlank)
                .distinct()
    }

    override fun findApplicationDataById(id: String): ApplicationData? {
        return cache[id]
    }

    override fun findAllApplicationData(affiliations: List<String>?): List<ApplicationData> {
        return cache
                .map { it.value }
                .filter { if (affiliations == null) true else affiliations.contains(it.affiliation) }
    }

    // @Scheduled(fixedRate = 300000, initialDelay = 360)
    fun refreshCache(affiliations: List<String>? = null) {

        val allKeys = cache.keys().toList()
        val newKeys = mutableListOf<String>()

        val time = withStopWatch {
            val applications = applicationDataService.findAllApplicationData(affiliations)
            applications.forEach {
                cache[it.id] = it
                newKeys.add(it.id)
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

    fun withStopWatch(block: () -> Unit): StopWatch {
        return StopWatch().also {
            it.start()
            block()
            it.stop()
        }
    }
}