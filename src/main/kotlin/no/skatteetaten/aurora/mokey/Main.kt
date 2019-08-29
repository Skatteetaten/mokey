package no.skatteetaten.aurora.mokey

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class Main

fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}

private val logger = KotlinLogging.logger {}

@Component
class CacheWarmup(
    val applicationDataService: ApplicationDataService
) : InitializingBean {

    override fun afterPropertiesSet() {
        try {
            applicationDataService.cacheAtStartup()
        } catch (e: Exception) {
            logger.error("Unable to refresh cache during initialization", e)
        }
    }
}
