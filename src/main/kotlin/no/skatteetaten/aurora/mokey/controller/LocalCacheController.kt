package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/load")
//@Profile("local")
class LocalCacheController(val auroraApplicationCacheService: AuroraApplicationCacheService, val openShiftService: OpenShiftService) {

    @GetMapping()
    fun get(@RequestParam namespace: List<String>) {
        auroraApplicationCacheService.load(namespace)
    }

    @GetMapping("/all")
    fun all() {
        val projects = openShiftService.projects().map { it.metadata.name }
        auroraApplicationCacheService.load(projects)
    }


}