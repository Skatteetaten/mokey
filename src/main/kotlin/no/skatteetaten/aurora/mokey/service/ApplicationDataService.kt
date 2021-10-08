package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.extensions.LABEL_AFFILIATION
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentSpec
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
    @Value("\${mokey.crawler.timeout:3m}") val crawlerTimeout: Duration,
) {
    val affiliations: List<String>
        get() = if (affiliationsConfig.isBlank()) emptyList()
        else affiliationsConfig.split(",").map { it.trim() }
    val cache = ConcurrentHashMap<String, ApplicationData>()

    fun findPublicApplicationDataByApplicationDeploymentId(id: String): ApplicationPublicData? = cache[id]?.publicData

    suspend fun findPublicApplicationDataByApplicationDeploymentRef(
        applicationDeploymentRefs: List<ApplicationDeploymentRef>,
        cached: Boolean = true,
    ): List<ApplicationPublicData> {
        val cachedElements = cache.filter {
            val publicData = it.value.publicData

            applicationDeploymentRefs.contains(
                ApplicationDeploymentRef(
                    publicData.environment,
                    publicData.applicationDeploymentName
                )
            )
        }.values

        return when (cached) {
            true -> cachedElements.map { it.publicData }
            false -> {
                val deployments = cachedElements.map {
                    val deployment = ApplicationDeployment(
                        spec = ApplicationDeploymentSpec(
                            applicationId = it.applicationId ?: "",
                            applicationName = it.applicationName,
                            applicationDeploymentId = it.applicationDeploymentId,
                            applicationDeploymentName = it.applicationDeploymentName
                        )
                    )
                    deployment.metadata {
                        name = it.applicationName
                        namespace = it.namespace
                        labels = mapOf(
                            LABEL_AFFILIATION to it.affiliation
                        )
                    }

                    deployment
                }

                applicationDataService.findAllApplicationDataByEnvironments(deployments).onEach {
                    addCacheEntry(it.applicationDeploymentId, it)
                }.map {
                    it.publicData
                }
            }
        }
    }

    fun findAllPublicApplicationDataByApplicationId(id: String): List<ApplicationPublicData> =
        findAllPublicApplicationData().filter { it.applicationId == id }

    fun findAllPublicApplicationData(
        affiliations: List<String> = emptyList(),
        ids: List<String> = emptyList(),
    ): List<ApplicationPublicData> = cache.map { it.value.publicData }
        .filter { if (affiliations.isEmpty()) true else affiliations.contains(it.affiliation) }
        .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }

    fun findAllAffiliations(): List<String> = cache.mapNotNull { it.value.affiliation }
        .filter(String::isNotBlank)
        .distinct()

    suspend fun findAllVisibleAffiliations(): List<String> = getFromCacheForUser()
        .mapNotNull { it.affiliation }
        .filter(String::isNotBlank)
        .distinct()

    suspend fun findApplicationDataByApplicationDeploymentId(id: String): ApplicationData? =
        getFromCacheForUser(id).firstOrNull()

    suspend fun findAllApplicationData(
        affiliations: List<String> = emptyList(),
        ids: List<String> = emptyList(),
    ): List<ApplicationData> = getFromCacheForUser()
        .filter { if (affiliations.isEmpty()) true else affiliations.contains(it.affiliation) }
        .filter { if (ids.isEmpty()) true else ids.contains(it.applicationDeploymentId) }

    @DelicateCoroutinesApi
    @Scheduled(
        fixedDelayString = "\${mokey.crawler.rateSeconds:120000}",
        initialDelayString = "\${mokey.crawler.delaySeconds:120000}",
    )
    fun cache() {
        runCatching {
            GlobalScope.launch(IO) {
                withTimeout(crawlerTimeout.toMillis()) {
                    refreshCache(affiliations)
                }
            }
        }.onFailure {
            when (it) {
                is TimeoutCancellationException -> logger.warn("Timed out running crawler")
                is WebClientResponseException.TooManyRequests ->
                    logger.warn("Aborting due to too many requests, aborting the current crawl")
                is InterruptedException -> logger.info("Interrupted")
                is Error -> {
                    logger.error("Error when running crawler", it)

                    throw it
                }
                else -> {
                    val rootCause = ExceptionUtils.getRootCauseMessage(it)

                    logger.error(
                        "Exception in schedule, " +
                            "type=${it::class.simpleName} " +
                            "msg=\"${it.localizedMessage}\" " +
                            "rootCause=\"$rootCause\""
                    )
                }
            }
        }
    }

    suspend fun refreshItem(applicationDeploymentId: String) = findApplicationDataByApplicationDeploymentId(
        applicationDeploymentId
    )?.let { current ->
        val data = applicationDataService.createSingleItem(
            current.namespace,
            current.applicationDeploymentName
        )

        addCacheEntry(applicationDeploymentId, data)
    } ?: throw IllegalArgumentException("ApplicationDeploymentId=$applicationDeploymentId is not cached")

    suspend fun cacheAtStartup() =
        applicationDataService.findAndGroupAffiliations(affiliations).forEach { refreshAffiliation(it.key, it.value) }

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
        env: List<Environment>,
    ): List<ApplicationData> {
        val applicationDeployments: List<ApplicationDeployment> = applicationDataService.findAllApplicationDeployments(
            env
        )
        val previousKeys = findCacheKeysForGivenAffiliation(affiliation)
        val newKeys = applicationDeployments.map { it.spec.applicationDeploymentId }

        (previousKeys - newKeys).forEach {
            logger.info("Remove application since it does not exist anymore applicationDeploymentId={}", it)
            removeCacheEntry(it)
        }

        if (applicationDeployments.isEmpty()) return emptyList()

        val watch = StopWatch().also {
            it.start()
        }

        val applications = applicationDataService.findAllApplicationDataByEnvironments(applicationDeployments)

        watch.stop()

        applications.forEach {
            logger.debug(
                "Added cache for " +
                    "deploymentId=${it.applicationDeploymentId} " +
                    "name=${it.applicationDeploymentName} " +
                    "namespace=${it.namespace}"
            )

            addCacheEntry(it.applicationDeploymentId, it)
        }

        if (applications.isNotEmpty()) {
            logger.info(
                "Apps cached " +
                    "affiliation=$affiliation " +
                    "apps=${applications.size} " +
                    "time=${watch.totalTimeSeconds}"
            )
        }

        return applications
    }

    private fun findCacheKeysForGivenAffiliation(affiliation: String): List<String> = cache
        .filter { it.value.affiliation == affiliation }
        .map { it.key }

    /**
     * Gets elements from the cache that can be accessed by the current user
     */
    suspend fun getFromCacheForUser(id: String? = null): List<ApplicationData> {
        val values = if (id != null) listOfNotNull(cache[id]) else cache.map { it.value }
        val projectNames = client.getAllProjects().map { it.metadata.name }

        return values.filter { projectNames.contains(it.namespace) }
    }
}
