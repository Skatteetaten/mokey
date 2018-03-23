package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
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
        val applicationDataService: ApplicationDataServiceCacheDecorator,
        @Value("\${mokey.cache.affiliations:}") val affiliationsConfig: String
) : InitializingBean {

    val affiliations: List<String>?
        get() = if (affiliationsConfig.isBlank()) null
        else affiliationsConfig.split(",").map { it.trim() }

    override fun afterPropertiesSet() {
        applicationDataService.refreshCache(affiliations)
    }
}