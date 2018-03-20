package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.CachedApplicationDataService
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class Main(val applicationDataService: CachedApplicationDataService) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val environments = listOf("aurora", "paas", "sirius-utv1").map { Environment.fromNamespace(it) }
        applicationDataService.refreshCache(/*environments*/)
    }
}

fun main(args: Array<String>) {
    SpringApplication.run(Main::class.java, *args)
}
