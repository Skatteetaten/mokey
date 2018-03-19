package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.Environment
import no.skatteetaten.aurora.mokey.service.CachedApplicationDataService
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/load")
//@Profile("local")
class LocalCacheController(val cachedApplicationDataService: CachedApplicationDataService, val openShiftService: OpenShiftService) {

    @GetMapping()
    fun get(@RequestParam namespaces: List<String>) {
        cachedApplicationDataService.refreshCache(namespaces.map { Environment.fromNamespace(it) })
    }

    @GetMapping("/all")
    fun all() {
        cachedApplicationDataService.refreshCache()
    }
}