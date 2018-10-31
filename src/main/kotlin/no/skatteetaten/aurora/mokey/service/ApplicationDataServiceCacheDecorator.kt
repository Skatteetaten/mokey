package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.service.DataSources.CACHE
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
@Primary
@ConditionalOnProperty(name = ["mokey.cache.enabled"], matchIfMissing = true)
@ApplicationDataSource(CACHE)
class ApplicationDataServiceCacheDecorator(
    val applicationDataService: ApplicationDataServiceOpenShift,
    val openShiftService: OpenShiftService,
    @Value("\${mokey.cache.affiliations:}") val affiliationsConfig: String,
    @Value("\${mokey.crawler.sleep:1s}") val sleep: Duration

) : ApplicationDataService {

    val affiliations: List<String>
        get() = if (affiliationsConfig.isBlank()) emptyList()
        else affiliationsConfig.split(",").map { it.trim() }

    val cache = ConcurrentHashMap<String, ApplicationData>()

    val logger: Logger = LoggerFactory.getLogger(ApplicationDataServiceCacheDecorator::class.java)

    override fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData? {
        return cache[id]?.publicData
    }

    override fun findAllPublicApplicationData(
        affiliations: List<String>,
        ids: List<String>
    ): List<ApplicationPublicData> {
        return cache.map { it.value.publicData }
            .filter { if (affiliations.isEmpty()) true else affiliations.contains(it.affiliation) }
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }
    }

    override fun findAllVisibleAffiliations(): List<String> =
        getFromCacheForUser()
            .mapNotNull { it.affiliation }
            .filter(String::isNotBlank)
            .distinct()

    override fun findAllAffiliations(): List<String> {
        return cache.mapNotNull { it.value.affiliation }
            .filter(String::isNotBlank)
            .distinct()
    }

    override fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? =
        getFromCacheForUser(id).firstOrNull()

    override fun findAllApplicationData(affiliations: List<String>, ids: List<String>): List<ApplicationData> =
        getFromCacheForUser()
            .filter { if (affiliations.isEmpty()) true else affiliations.contains(it.affiliation) }
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }

    // TODO: property
    @Scheduled(fixedRateString = "\${mokey.crawler.rate:2m}", initialDelayString = "\${mokey.crawler.delay:2m}")
    fun cache() = refreshCache(affiliations)

    fun refreshItem(applicationId: String) =
        findApplicationDataByApplicationDeploymentId(applicationId)?.let { current ->
            val data = applicationDataService.createSingleItem(current.namespace, current.applicationDeploymentName)
            cache[applicationId] = data
        } ?: throw IllegalArgumentException("ApplicationId=$applicationId is not cached")

    fun refreshCache(affiliationInput: List<String> = emptyList()) {

        val affiliations = applicationDataService.findAllAffiliations(affiliationInput)

        affiliations.forEach { affiliation ->

            val applications = refreshDeployments(affiliation)
            val previousKeys = findCacheKeysForGivenAffiliation(affiliation)
            val newKeys = applications.map { it.applicationDeploymentId }

            (previousKeys - newKeys).forEach {
                logger.info("Remove application since it does not exist anymore applicationDeploymentId={}", it)
                cache.remove(it)
            }

            Thread.sleep(sleep.toMillis())
        }
    }

    private fun refreshDeployments(affiliation: String): List<ApplicationData> {
        val applications = mutableListOf<ApplicationData>()
        val time = withStopWatch {
            applications += applicationDataService.findAllApplicationData(affiliation)
        }

        applications.forEach {
            logger.debug("Added cache for deploymentId=${it.applicationDeploymentId} name=${it.applicationDeploymentName} namespace=${it.namespace}")
            cache[it.applicationDeploymentId] = it
        }

        logger.info("number of affiliation=$affiliation apps={} time={}", applications.size, time.totalTimeSeconds)

        return applications
    }

    private fun findCacheKeysForGivenAffiliation(affiliation: String): List<String> {
        return cache
            .filter { it.value.affiliation == affiliation }
            .map { it.key }
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
