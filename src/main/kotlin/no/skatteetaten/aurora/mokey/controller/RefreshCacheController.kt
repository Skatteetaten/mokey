package no.skatteetaten.aurora.mokey.controller

import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RefreshParams(val applicationDeploymentId: String?, val affiliations: List<String>?)

@RestController
@RequestMapping("/api/auth/refresh")
class RefreshCacheController(val crawlService: ApplicationDataService) {

    @PostMapping
    fun refreshCache(@RequestBody params: RefreshParams) {

        if ((params.applicationDeploymentId ?: params.affiliations) == null) {
            throw IllegalArgumentException("Must specify one of: ['affiliations', 'applicationDeploymentId'] as parameter to refresh.")
        }

        // OVERFORING her starter refresh av cache for 1 affiliation fra consollet
        params.affiliations?.let {
            runBlocking { crawlService.refreshCache(it) }
        }

        params.applicationDeploymentId?.let {
            crawlService.refreshItem(it)
        }
    }
}
