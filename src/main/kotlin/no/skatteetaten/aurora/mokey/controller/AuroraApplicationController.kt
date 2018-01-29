package no.skatteetaten.aurora.mokey.controller

import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.openshift.client.DefaultOpenShiftClient
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newFixedThreadPoolContext
import kotlinx.coroutines.experimental.runBlocking
import no.skatteetaten.aurora.mokey.extensions.getOrNull
import no.skatteetaten.aurora.mokey.service.AuroraApplicationService
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.model.Response
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.NoAccessException
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.*
import org.springframework.util.StopWatch
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern


@RestController
@RequestMapping("/aurora/application")
class AuroraApplicationController(val auroraApplicationCacheService: AuroraApplicationCacheService) {

    val logger: Logger = LoggerFactory.getLogger(AuroraApplicationController::class.java)



    //TODO: Bytt ut med spring security
    private fun checkPermissions(authorization: String, namespace: String) {
        val headerPattern: Pattern = Pattern.compile("Bearer\\s+(.*)", Pattern.CASE_INSENSITIVE)
        val matcher = headerPattern.matcher(authorization)
        if (!matcher.find()) {
            throw NoAccessException("Unexpected Authorization header format")
        }
        val token = matcher.group(1)
        try {
            logger.debug("lager klient")
            val client = DefaultOpenShiftClient(ConfigBuilder().withOauthToken(token).build())
            logger.debug("henter prosjekt")
            client.projects().withName(namespace).getOrNull()
        } catch (e: Exception) {
            throw NoAccessException("An unexpected error occurred while getting OpenShift user")
        } ?: throw NoAccessException("No access")

        logger.debug("fant prosjekt")
    }




    @GetMapping("/namespace/{namespace}/application/{application}")
    fun get(@PathVariable namespace: String, @PathVariable application: String,
            @RequestHeader(value = "Authorization") authorization: String
    ): Response {
        checkPermissions(authorization, namespace)

        logger.debug("finner applikasjon")
        val appKey = "$namespace/$application"
        return auroraApplicationCacheService.get(appKey)?.let {
            Response(items = listOf(it))
        } ?: Response(success = false, message = "Does not exist", items = emptyList())
    }
}


