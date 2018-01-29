package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.Response
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/aurora/application")
class AuroraApplicationController(val auroraApplicationCacheService: AuroraApplicationCacheService) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationController::class.java)


    @GetMapping("/namespace/{namespace}/application/{application}")
    fun get(@PathVariable namespace: String, @PathVariable application: String): Response {

        logger.debug("finner applikasjon")
        val appKey = "$namespace/$application"
        return auroraApplicationCacheService.get(appKey)?.let {
            Response(items = listOf(it))
        } ?: Response(success = false, message = "Does not exist", items = emptyList())
    }
}


