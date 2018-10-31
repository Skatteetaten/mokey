package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@SpringBootApplication
class Main

fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}

@Component
@ConditionalOnProperty(name = ["mokey.cache.enabled"], matchIfMissing = true)
class CacheWarmup(
    val applicationDataService: ApplicationDataServiceCacheDecorator
) : InitializingBean {

    private val logger = LoggerFactory.getLogger(CacheWarmup::class.java)

    override fun afterPropertiesSet() {
        try {
            applicationDataService.cacheAtStartup()
        } catch (e: Exception) {
            logger.error("Unable to refresh cache during initialization", e)
        }
    }
}
