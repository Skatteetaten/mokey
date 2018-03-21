package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class Main

fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}

@Component
class CacheWarmup(val applicationDataService: ApplicationDataServiceCacheDecorator) : InitializingBean {
    override fun afterPropertiesSet() {
        val environments = listOf("aurora", "paas", "sirius-utv1")//.map { Environment.fromNamespace(it) }
        applicationDataService.refreshCache(environments)

    }
}