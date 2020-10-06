package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.Environment
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataService(
    val applicationDataService: ApplicationDataServiceOpenShift,
    val client: OpenShiftUserClient,
    @Value("\${mokey.cache.affiliations:}") val affiliationsConfig: String,
    val statusRegistry: ApplicationStatusRegistry,
    @Value("\${mokey.crawler.timeout:3m}") val crawlerTimeout: Duration
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

    fun findPublicApplicationDataByApplicationDeploymentRef(applicationDeploymentRefs: List<ApplicationDeploymentRef>): List<ApplicationPublicData> {
        return cache.filter {
            val publicData = it.value.publicData
            applicationDeploymentRefs.contains(ApplicationDeploymentRef(publicData.environment, publicData.applicationDeploymentName))
        }.ifEmpty { return emptyList() }.values.map { it.publicData }
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

    @Scheduled(
        fixedDelayString = "\${mokey.crawler.rateSeconds:120000}",
        initialDelayString = "\${mokey.crawler.delaySeconds:120000}"
    )
    fun cache() {
        kotlin.runCatching {
            runBlocking(MDCContext()) {
                withTimeout(crawlerTimeout.toMillis()) {
                    refreshCache(affiliations)
                }
            }
        }.onFailure {
            when (it) {
                is TimeoutCancellationException -> {
                    logger.warn("Timed out running crawler")
                }
                is WebClientResponseException.TooManyRequests -> {
                    logger.warn("Aborting due to too many requests, aborting the current crawl")
                }
                is InterruptedException -> {
                    logger.info("Interrupted")
                }
                is Error -> {
                    logger.error("Error when running crawler", it)
                    throw it
                }
                else -> {
                    val rootCause = ExceptionUtils.getRootCauseMessage(it)
                    logger.error(
                        "Exception in schedule, type=${it::class.simpleName} msg=\"${it.localizedMessage}\" rootCause=\"$rootCause\""
                    )
                }
            }
        }
    }

    fun refreshItem(applicationDeploymentId: String) =
        findApplicationDataByApplicationDeploymentId(applicationDeploymentId)?.let { current ->
            val data = runBlocking(MDCContext()) {
                applicationDataService.createSingleItem(
                    current.namespace,
                    current.applicationDeploymentName
                )
            }
            addCacheEntry(applicationDeploymentId, data)
        } ?: throw IllegalArgumentException("ApplicationDeploymentId=$applicationDeploymentId is not cached")

    fun cacheAtStartup() {
        runBlocking(MDCContext()) {
            val affiliation = applicationDataService.findAndGroupAffiliations(affiliations)
            affiliation.forEach { refreshAffiliation(it.key, it.value) }
        }
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

    suspend fun refreshCache(affiliationInput: List<String> = emptyList()) {

        val watch = StopWatch().also { it.start() }
        val affiliations = applicationDataService.findAndGroupAffiliations(affiliationInput)

        affiliations.forEach { (affiliation, env) ->
            refreshAffiliation(affiliation, env)
        }
        val time: Double = watch.let {
            it.stop()
            it.totalTimeSeconds
        }
        logger.info("Crawler done total cached=${cache.keys.size} timeSeconds=$time")
    }

    private suspend fun refreshAffiliation(
        affiliation: String,
        env: List<Environment>
    ): List<ApplicationData> {

        val applicationDeployments: List<ApplicationDeployment> =
            applicationDataService.findAllApplicationDeployments(env)

        val previousKeys = findCacheKeysForGivenAffiliation(affiliation)
        val newKeys = applicationDeployments.map { it.spec.applicationDeploymentId }

        (previousKeys - newKeys).forEach {
            logger.info("Remove application since it does not exist anymore applicationDeploymentId={}", it)
            removeCacheEntry(it)
        }

        if (applicationDeployments.isEmpty()) {
            return emptyList()
        }

        val watch = StopWatch().also {
            it.start()
        }
        val applications = applicationDataService.findAllApplicationDataByEnvironments(applicationDeployments)
        watch.stop()

        applications.forEach {
            logger.debug("Added cache for deploymentId=${it.applicationDeploymentId} name=${it.applicationDeploymentName} namespace=${it.namespace}")
            addCacheEntry(it.applicationDeploymentId, it)
        }

        if (applications.isNotEmpty()) {
            logger.info("Apps cached affiliation=$affiliation apps=${applications.size} time=${watch.totalTimeSeconds}")
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

        val projectNames = runBlocking { client.getAllProjects() }
            .map { it.metadata.name }

        return values.filter { projectNames.contains(it.namespace) }
    }
}
