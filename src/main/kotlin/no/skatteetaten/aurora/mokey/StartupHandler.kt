package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.springframework.beans.factory.InitializingBean
import org.springframework.stereotype.Component

@Component
class StartupHandler(val applicationDataService: ApplicationDataServiceCacheDecorator) : InitializingBean {
    override fun afterPropertiesSet() {
        val environments = listOf("aurora", "paas", "sirius-utv1").map { Environment.fromNamespace(it) }
        applicationDataService.refreshCache(/*environments*/)
    }
}