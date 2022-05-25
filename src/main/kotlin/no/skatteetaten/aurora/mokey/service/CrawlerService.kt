package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.util.StopWatch
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration

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
        cacheableServices.forEach { it.refreshCache(groupedAffiliations) }
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
