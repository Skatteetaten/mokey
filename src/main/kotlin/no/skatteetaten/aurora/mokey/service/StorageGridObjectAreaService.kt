package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.StorageGridObjectArea
import no.skatteetaten.aurora.mokey.model.StorageGridObjectAreaDetails
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
class StorageGridObjectAreaService(
    val userClient: OpenShiftUserClient,
    val applicationDataService: ApplicationDataServiceOpenShift,
    val openShiftServiceAccountClient: OpenShiftServiceAccountClient,
    @Value("\${openshift.cluster}") val cluster: String,
    @Value("\${mokey.crawler.timeout:3m}") val crawlerTimeout: Duration,
    @Value("\${mokey.cache.affiliations:}") val affiliationsConfig: String,
) {
    val affiliations: List<String>
        get() = if (affiliationsConfig.isBlank()) emptyList()
        else affiliationsConfig.split(",").map { it.trim() }
    val cache = ConcurrentHashMap<String, List<StorageGridObjectArea>>()

    suspend fun getAllStorageGridObjectAreasForAffiliationFromCache(affiliation: String): List<StorageGridObjectAreaDetails> {
        val projects = userClient.getProjectsInAffiliation(affiliation)

        val sgoas = projects.flatMap { project ->
            cache[cacheKey(affiliation, project.metadata.name)].orEmpty()
        }
        return sgoas.map {
            // TODO: retrieve tenant and bucketname from status
            val tenant = "$affiliation-$cluster"
            StorageGridObjectAreaDetails(
                name = it.metadata.name,
                namespace = it.metadata.namespace,
                creationTimestamp = it.metadata.creationTimestamp,
                objectArea = it.spec.objectArea,
                tenant = tenant,
                bucketName = "$tenant-${it.spec.bucketPostfix}",
                message = it.status.result.message,
                reason = it.status.result.reason,
                success = it.status.result.success
            )
        }
    }

    private fun cacheKey(affiliation: String, projectName: String) = "$affiliation.$projectName"

    @DelicateCoroutinesApi
    @Scheduled(
        fixedDelayString = "\${mokey.crawler.rateSeconds:120000}",
        initialDelayString = "\${mokey.crawler.delaySeconds:120000}",
    )
    fun cache() {
        logger.debug("start cache crawl")
        kotlin.runCatching {
            runBlocking {
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

    suspend fun cacheAtStartup() = applicationDataService.findAndGroupAffiliations(affiliations).forEach {
        refreshStorageGridObjectArea(it.key)
    }

    private suspend fun findAllStorageGridObjectAreasForProject(projectName: String): List<StorageGridObjectArea> {
        return openShiftServiceAccountClient.getStorageGridObjectAreas(projectName)
    }

    private suspend fun refreshCache(affiliationInput: List<String> = emptyList()) {
        val watch = StopWatch().also { it.start() }
        val affiliations = applicationDataService.findAndGroupAffiliations(affiliationInput)

        affiliations.forEach { (affiliation) ->
            refreshStorageGridObjectArea(affiliation)
        }

        val time: Double = watch.let {
            it.stop()
            it.totalTimeSeconds
        }

        logger.info("Crawler done total cached=${cache.keys.size} timeSeconds=$time")
    }

    private suspend fun refreshStorageGridObjectArea(affiliation: String): Map<String, List<StorageGridObjectArea>> {
        val watch = StopWatch().also { it.start() }

        val projects = openShiftServiceAccountClient.getProjectsInAffiliation(affiliation)
        val storageGridObjectAreas = projects.map { project ->
            val projectName = project.metadata.name
            val sgoas: List<StorageGridObjectArea> = findAllStorageGridObjectAreasForProject(projectName)
            cache[cacheKey(affiliation, projectName)] = sgoas
            logger.debug(
                "Added cache for " +
                    "affiliation=$affiliation " +
                    "project=$projectName " +
                    "cached=${sgoas.size}"
            )
            projectName to sgoas
        }.toMap()

        watch.stop()
        return storageGridObjectAreas
    }
}
