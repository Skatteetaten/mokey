package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.Environment
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

interface CacheService {
    /**
     * cacheAtStartup primes the cache and will run when the application starts.
     * The application will not be ready before the cache priming is complete.
     */
    suspend fun cacheAtStartup(groupedAffiliations: Map<String, List<Environment>>)

    /**
     * refreshCache refreshes the cache for the provided affiliations.
     * If affiliations is empty then the cache is refreshed for all affiliations.
     */
    suspend fun refreshCache(groupedAffiliations: Map<String, List<Environment>>)

    /**
     * refreshItem refreshes the cache for the given input parameter.
     */
    suspend fun refreshItem(applicationDeploymentId: String)

    suspend fun refreshResource(affiliation: String, env: List<Environment>)
}

private val logger = KotlinLogging.logger {}

@Component
class CrawlerService(
    val applicationDataServiceOpenShift: ApplicationDataServiceOpenShift,
    val cacheableServices: List<CacheService>,
    @Value("\${mokey.cache.affiliations:}") val affiliationsConfig: String,
    @Value("\${mokey.crawler.timeout:3m}") val crawlerTimeout: Duration,
) {
    val affiliations: List<String>
        get() = if (affiliationsConfig.isBlank()) emptyList()
        else affiliationsConfig.split(",").map { it.trim() }

    @DelicateCoroutinesApi
    @Scheduled(
        fixedDelayString = "\${mokey.crawler.rateSeconds:120000}",
        initialDelayString = "\${mokey.crawler.delaySeconds:120000}",
    )
    fun cache() {
        println(affiliations.size)
        kotlin.runCatching {
            runBlocking {
                val groupedAffiliations = applicationDataServiceOpenShift.findAndGroupAffiliations(affiliations)
                withTimeout(crawlerTimeout.toMillis()) {
                    cacheableServices.forEach {
                        it.refreshCache(groupedAffiliations)
                    }
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

    suspend fun cacheAtStartup() {
        val stopWatch = StopWatch().also { it.start() }
        val groupedAffiliations = applicationDataServiceOpenShift.findAndGroupAffiliations(affiliations)
        cacheableServices.forEach { it.cacheAtStartup(groupedAffiliations) }
        stopWatch.also {
            it.stop()
            logger.info { "Cache priming completed timeSeconds=${it.totalTimeSeconds}" }
        }
    }

    suspend fun refreshItem(applicationDeploymentId: String) {
        cacheableServices.forEach { it.refreshItem(applicationDeploymentId) }
    }

    suspend fun refreshCache(affiliationInput: List<String> = emptyList()) {
        val affiliations = applicationDataServiceOpenShift.findAndGroupAffiliations(affiliationInput)
        cacheableServices.forEach { it.refreshCache(affiliations) }
    }
}
