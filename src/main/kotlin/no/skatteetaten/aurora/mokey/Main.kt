package no.skatteetaten.aurora.mokey

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.lang.Thread.sleep

@SpringBootApplication
class Main

fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}

private val logger = KotlinLogging.logger {}

@ConditionalOnProperty(name = ["mokey.cachewarmup.enabled"], havingValue = "true", matchIfMissing = true)
@Component
class CacheWarmup(
    val applicationDataService: ApplicationDataService,
) : InitializingBean {
    override fun afterPropertiesSet() {
        warmUp(attempt = 0)
    }

    private fun warmUp(attempt: Int) {
        if (attempt < 5) {
            runCatching {
                runBlocking { applicationDataService.cacheAtStartup() }
            }.getOrElse {
                when {
                    attempt < 4 -> {
                        logger.warn("failed cache during initialization, sleep for 10s and try again.")

                        sleep(10000)

                        warmUp(attempt + 1)
                    }
                    else -> {
                        val errorMsg = "Unable to refresh cache during initialization"

                        when (it) {
                            is Error -> {
                                logger.error(errorMsg, it)
                            }
                            else -> {
                                logger.error("$errorMsg, ${it.localizedMessage}")
                            }
                        }
                    }
                }
            }
        }
    }
}
