package no.skatteetaten.aurora.mokey.service

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.model.StorageGridObjectArea
import org.springframework.stereotype.Service
import org.springframework.util.StopWatch
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class StorageGridObjectAreaService(
    val userClient: OpenShiftUserClient,
    val openShiftServiceAccountClient: OpenShiftServiceAccountClient
) : CacheService {
    val cache = ConcurrentHashMap<String, List<StorageGridObjectArea>>()

    suspend fun getStorageGridObjectAreasForAffiliationFromCache(affiliation: String): List<StorageGridObjectArea> {
        // get projects user has access to, otherwise user may see data they are unauthorized to see.
        val projects = userClient.getProjectsInAffiliation(affiliation)

        if (projects.isEmpty()) {
            return emptyList()
        }

        return projects.flatMap {
            cache[cacheKey(affiliation, it.metadata.name)].orEmpty()
        }
    }

    override suspend fun refreshCache(groupedAffiliations: Map<String, List<Environment>>) {
        val time = refreshCacheForAffiliations(groupedAffiliations)
        logger.info("Crawler done total cached=${cache.values.flatten().size} timeSeconds=$time")
    }

    override suspend fun refreshItem(applicationDeploymentId: String) {
        // TODO: ref. AOS-6741 implement refreshItem function to update SGOA resource for applicationDeploymentId
    }

    override suspend fun refreshResource(affiliation: String, env: List<Environment>) {
        val watch = StopWatch().also { it.start() }

        val projects = openShiftServiceAccountClient.getProjectsInAffiliation(affiliation)
        projects.forEach { project ->
            val projectName = project.metadata.name
            val sgoas: List<StorageGridObjectArea> = openShiftServiceAccountClient.getStorageGridObjectAreas(projectName)
            cache[cacheKey(affiliation, projectName)] = sgoas
            logger.debug(
                "Added cache for " +
                    "affiliation=$affiliation " +
                    "project=$projectName " +
                    "cached=${sgoas.size}"
            )
        }

        watch.stop()
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

    private fun cacheKey(affiliation: String, projectName: String) = "$affiliation.$projectName"
}
