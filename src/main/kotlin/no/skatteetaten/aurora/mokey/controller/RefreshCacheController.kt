package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class RefreshParams(val applicationDeploymentId: String)
@RestController
@RequestMapping("/refresh")
class RefreshCacheController(val crawlService: ApplicationDataServiceCacheDecorator) {

    @PostMapping
    fun refreshCache(@RequestBody params: RefreshParams) {
        crawlService.refreshItem(params.applicationDeploymentId)
    }
}