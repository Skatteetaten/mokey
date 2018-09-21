package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.service.DataSources.CACHE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

@Service
@Primary
@ConditionalOnProperty(name = ["mokey.cache.enabled"], matchIfMissing = true)
@ApplicationDataSource(CACHE)
class ApplicationDataServiceCacheDecorator(
    val applicationDataService: ApplicationDataServiceOpenShift,
    val openShiftService: OpenShiftService
) : ApplicationDataService {

    // TODO: replace with Redis
    val cache = ConcurrentHashMap<String, ApplicationData>()

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceCacheDecorator::class.java)

    override fun findAllAffiliations(): List<String> {
        return cache.mapNotNull { it.value.affiliation }
            .filter(String::isNotBlank)
            .distinct()
    }

    override fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? =
        getFromCacheForUser(id).firstOrNull()

    override fun findAllApplicationData(affiliations: List<String>?): List<ApplicationData> =
        getFromCacheForUser()
            .filter { if (affiliations == null) true else affiliations.contains(it.affiliation) }

    // TODO: property
    @Scheduled(fixedRate = 120_000, initialDelay = 120_000)
    fun cache() = refreshCache()

    fun refreshItem(applicationId: String) =
        findApplicationDataByApplicationDeploymentId(applicationId)?.let { current ->
            val data = applicationDataService.createSingleItem(current.namespace, current.applicationDeploymentName)
            cache[applicationId] = data
        } ?: throw IllegalArgumentException("ApplicationId=$applicationId is not cached")

    fun refreshCache(affiliations: List<String>? = null) {

        val allKeys = cache.keys().toList()
        val newKeys = mutableListOf<String>()

        val time = withStopWatch {
            val applications = applicationDataService.findAllApplicationData(affiliations)
            applications.forEach {
                cache[it.applicationDeploymentId] = it
                newKeys.add(it.applicationDeploymentId)
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

    /**
     * Gets elements from the cache that can be accessed by the current user
     */
    private fun getFromCacheForUser(id: String? = null): List<ApplicationData> {

        val values = if (id != null) listOfNotNull(cache[id]) else cache.map { it.value }

        val projectNames = openShiftService.projectsForUser().map { it.metadata.name }

        return values.filter { projectNames.contains(it.namespace) }
    }

    private fun withStopWatch(block: () -> Unit): StopWatch {
        return StopWatch().also {
            it.start()
            block()
            it.stop()
        }
    }
}