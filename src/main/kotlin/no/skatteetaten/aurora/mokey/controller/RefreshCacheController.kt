package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RefreshParams(val applicationDeploymentId: String?, val affiliations: List<String>?)

@RestController
@RequestMapping("/api/auth/refresh")
@ConditionalOnProperty(name = ["mokey.cache.enabled"], matchIfMissing = true)
class RefreshCacheController(val crawlService: ApplicationDataServiceCacheDecorator) {

    @PostMapping
    fun refreshCache(@RequestBody params: RefreshParams) {

        if ((params.applicationDeploymentId ?: params.affiliations) == null) {
            throw IllegalArgumentException("Must specify one of: ['affiliations', 'applicationDeploymentId'] as parameter to refresh.")
        }

        params.affiliations?.let {
            crawlService.refreshCache(it)
        }

        params.applicationDeploymentId?.let {
            crawlService.refreshItem(it)
        }
    }
}