package no.skatteetaten.aurora.mokey.controller

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
    suspend fun refreshCache(@RequestBody params: RefreshParams) {
        require((params.applicationDeploymentId ?: params.affiliations) != null) {
            "Must specify one of: ['affiliations', 'applicationDeploymentId'] as parameter to refresh."
        }

        params.affiliations?.let { crawlService.refreshCache(it) }
        params.applicationDeploymentId?.let { crawlService.refreshItem(it) }
    }
}
