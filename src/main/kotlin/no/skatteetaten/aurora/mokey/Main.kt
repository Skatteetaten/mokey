package no.skatteetaten.aurora.mokey

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
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

private val logger = KotlinLogging.logger {}

@ConditionalOnProperty(name = ["mokey.cachewarmup.enabled"], havingValue = "true", matchIfMissing = true)
@Component
class CacheWarmup(
    val applicationDataService: ApplicationDataService
) : InitializingBean {

    override fun afterPropertiesSet() {
        try {
            applicationDataService.cacheAtStartup()
        } catch (e: Exception) {
            logger.info("failed cache during initialization, sleep for 10s and try again.")
            Thread.sleep(10000)
            try {
                applicationDataService.cacheAtStartup()
            } catch (e: Exception) {
                val errorMsg = "Unable to refresh cache during initialization"
                if (e is Error) {
                    logger.error(errorMsg, e)
                } else {
                    logger.error("$errorMsg, ${e.localizedMessage}")
                }
            }
        }
    }
}
