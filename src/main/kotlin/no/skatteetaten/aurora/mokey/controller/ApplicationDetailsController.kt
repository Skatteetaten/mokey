package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.controller.security.User
import no.skatteetaten.aurora.mokey.service.CachedApplicationDataService
import no.skatteetaten.aurora.mokey.service.NoSuchResourceException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/applicationdetails")
class ApplicationDetailsController(val cachedApplicationDataService: CachedApplicationDataService) {

    val logger: Logger = LoggerFactory.getLogger(ApplicationDetailsController::class.java)

    @GetMapping("/{id}")
    fun get(@PathVariable id: String, @AuthenticationPrincipal user: User): ApplicationDetails? {

        return cachedApplicationDataService.findApplicationDataById(id)?.let {
            ApplicationDetails(toApplication(it))
        } ?: throw NoSuchResourceException("Does not exist")
    }
}
