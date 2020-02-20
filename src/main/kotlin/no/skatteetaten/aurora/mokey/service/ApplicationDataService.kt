package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.Environment
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataService(
    val applicationDataService: ApplicationDataServiceOpenShift,
    val client: OpenShiftUserClient,
    @Value("\${mokey.cache.affiliations:}") val affiliationsConfig: String,
    @Value("\${mokey.crawler.sleepSeconds:1}") val sleep: Long,
    val statusRegistry: ApplicationStatusRegistry

) {
    val affiliations: List<String>
        get() = if (affiliationsConfig.isBlank()) emptyList()
        else affiliationsConfig.split(",").map { it.trim() }

    val cache = ConcurrentHashMap<String, ApplicationData>()

    /*
     * These methods work on public
     */
    fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData? {
        return cache[id]?.publicData
    }

    fun findAllPublicApplicationDataByApplicationId(id: String): List<ApplicationPublicData> =
        findAllPublicApplicationData().filter { it.applicationId == id }

    fun findAllPublicApplicationData(
        affiliations: List<String> = emptyList(),
        ids: List<String> = emptyList()
    ): List<ApplicationPublicData> {
        return cache.map { it.value.publicData }
            .filter { if (affiliations.isEmpty()) true else affiliations.contains(it.affiliation) }
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }
    }

    fun findAllAffiliations(): List<String> {
        return cache.mapNotNull { it.value.affiliation }
            .filter(String::isNotBlank)
            .distinct()
    }

    fun findAllVisibleAffiliations(): List<String> =
        getFromCacheForUser()
            .mapNotNull { it.affiliation }
            .filter(String::isNotBlank)
            .distinct()

    fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? =
        getFromCacheForUser(id).firstOrNull()

    fun findAllApplicationData(
        affiliations: List<String> = emptyList(),
        ids: List<String> = emptyList()
    ): List<ApplicationData> =
        getFromCacheForUser()
            .filter { if (affiliations.isEmpty()) true else affiliations.contains(it.affiliation) }
            .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }

    /*
    @Scheduled(
        fixedDelayString = "\${mokey.crawler.rateSeconds:120000}",
        initialDelayString = "\${mokey.crawler.delaySeconds:120000}"
    )
    fun cache() = refreshCache(affiliations)
     */

    fun refreshItem(applicationId: String) =
        findApplicationDataByApplicationDeploymentId(applicationId)?.let { current ->
            val data = applicationDataService.createSingleItem(current.namespace, current.applicationDeploymentName)
            addCacheEntry(applicationId, data)
        } ?: throw IllegalArgumentException("ApplicationId=$applicationId is not cached")

    fun cacheAtStartup() {
        applicationDataService.findAndGroupAffiliations(affiliations)
            .forEach { refreshAffiliation(it.key, it.value) }
    }

    private fun addCacheEntry(applicationId: String, data: ApplicationData) {
        cache[applicationId]?.let { old ->
            statusRegistry.update(old.publicData, data.publicData)
        } ?: statusRegistry.add(data.publicData)

        cache[applicationId] = data
    }

    private fun removeCacheEntry(applicationId: String) {
        cache[applicationId]?.let { app ->
            statusRegistry.remove(app.publicData)
            cache.remove(applicationId)
        }
    }

    fun refreshCache(affiliationInput: List<String> = emptyList()) {

        val watch = StopWatch().also { it.start() }
        val affiliations = applicationDataService.findAndGroupAffiliations(affiliationInput)

        affiliations.forEach { (affiliation, env) ->

            val affiliationWatch = StopWatch().also { it.start() }
            refreshAffiliation(affiliation, env)
            val affiliationTime = affiliationWatch.let {
                it.stop()
                it.totalTimeMillis
            }

            if (affiliationTime > 200) {
                Thread.sleep(sleep * 1000)
            }
        }
        val time: Double = watch.let {
            it.stop()
            it.totalTimeSeconds
        }
        logger.info("Crawler done total cached=${cache.keys.size} timeSeconds=$time")
    }

    private fun refreshAffiliation(
        affiliation: String,
        env: List<Environment>
    ) {
        val applications = refreshDeployments(affiliation, env)
        val previousKeys = findCacheKeysForGivenAffiliation(affiliation)
        val newKeys = applications.map { it.applicationDeploymentId }

        (previousKeys - newKeys).forEach {
            logger.info("Remove application since it does not exist anymore applicationDeploymentId={}", it)
            removeCacheEntry(it)
        }
    }

    private fun refreshDeployments(
        affiliation: String,
        env: List<Environment>
    ): List<ApplicationData> {
        val applications = mutableListOf<ApplicationData>()
        val time = withStopWatch {
            applications += applicationDataService.findAllApplicationDataForEnv(environments = env)
        }

        applications.forEach {
            logger.debug("Added cache for deploymentId=${it.applicationDeploymentId} name=${it.applicationDeploymentName} namespace=${it.namespace}")
            addCacheEntry(it.applicationDeploymentId, it)
        }

        if (applications.isNotEmpty()) {
            logger.info("Apps cached affiliation=$affiliation apps=${applications.size} time=${time.totalTimeSeconds}")
        }

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
    fun getFromCacheForUser(id: String? = null): List<ApplicationData> {

        val values = if (id != null) listOfNotNull(cache[id]) else cache.map { it.value }

        val projectNames = runBlocking { client.getAllProjects() }.map { it.metadata.name }

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
