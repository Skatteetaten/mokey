package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/load")
@Profile("local")
class LocalCacheController(val auroraApplicationCacheService: AuroraApplicationCacheService) {

    val logger: Logger = LoggerFactory.getLogger(LocalCacheController::class.java)

    @GetMapping("/{namespace}")
    fun get(@PathVariable namespace: String) {
        logger.debug("finner applikasjon")
        auroraApplicationCacheService.load(listOf(namespace))
    }


}