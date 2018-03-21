package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.CachedApplicationDataService
import org.springframework.boot.CommandLineRunner

class StartupHandler(val applicationDataService: CachedApplicationDataService) : CommandLineRunner {
    override fun run(vararg args: String?) {
        val environments = listOf("aurora", "paas", "sirius-utv1").map { Environment.fromNamespace(it) }
        applicationDataService.refreshCache(/*environments*/)

    }

}