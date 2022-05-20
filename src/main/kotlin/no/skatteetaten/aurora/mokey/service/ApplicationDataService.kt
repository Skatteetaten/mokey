package no.skatteetaten.aurora.mokey.service

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.extensions.LABEL_AFFILIATION
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeployment
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentSpec
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.Environment
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class ApplicationDataService(
    val applicationDataService: ApplicationDataServiceOpenShift,
    val client: OpenShiftUserClient,
    val statusRegistry: ApplicationStatusRegistry,
) : CacheService {
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

                applicationDataService.findAllApplicationDataByEnvironments(deployments).map {
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

    override suspend fun cacheAtStartup(groupedAffiliations: Map<String, List<Environment>>) {
        val time = refreshCacheForAffiliations(groupedAffiliations)
        logger.debug("Prime cache completed total cached=${cache.keys.size} timeSeconds=$time")
    }

    override suspend fun refreshCache(groupedAffiliations: Map<String, List<Environment>>) {
        val time = refreshCacheForAffiliations(groupedAffiliations)
        logger.info("Crawler done total cached=${cache.keys.size} timeSeconds=$time")
    }

    override suspend fun refreshItem(applicationDeploymentId: String) {
        findApplicationDataByApplicationDeploymentId(applicationDeploymentId)?.let { current ->
            val data = applicationDataService.createSingleItem(
                current.namespace,
                current.applicationDeploymentName
            )

            addCacheEntry(applicationDeploymentId, data)
        } ?: throw IllegalArgumentException("ApplicationDeploymentId=$applicationDeploymentId is not cached")
    }

    override suspend fun refreshResource(affiliation: String, env: List<Environment>) {
        val applicationDeployments: List<ApplicationDeployment> = applicationDataService.findAllApplicationDeployments(
            env
        )
        val previousKeys = findCacheKeysForGivenAffiliation(affiliation)
        val newKeys = applicationDeployments.map { it.spec.applicationDeploymentId }

        (previousKeys - newKeys).forEach {
            logger.info("Remove application since it does not exist anymore applicationDeploymentId={}", it)
            removeCacheEntry(it)
        }

        if (applicationDeployments.isEmpty()) return

        val watch = StopWatch().also { it.start() }

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
    }

    private suspend fun refreshCacheForAffiliations(groupedAffiliations: Map<String, List<Environment>>): Double {
        val watch = StopWatch().also { it.start() }

        groupedAffiliations.forEach {
            this.refreshResource(it.key, it.value)
        }

        return watch.let {
            it.stop()
            it.totalTimeSeconds
        }
    }

    private fun findCacheKeysForGivenAffiliation(affiliation: String): List<String> = cache
        .filter { it.value.affiliation == affiliation }
        .map { it.key }

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

    /**
     * Gets elements from the cache that can be accessed by the current user
     */
    suspend fun getFromCacheForUser(id: String? = null): List<ApplicationData> {
        val values = if (id != null) listOfNotNull(cache[id]) else cache.map { it.value }
        val projectNames = client.getAllProjects().map { it.metadata.name }

        return values.filter { projectNames.contains(it.namespace) }
    }
}
