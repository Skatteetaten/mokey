package no.skatteetaten.aurora.mokey.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.skatteetaten.aurora.mokey.extensions.asMap
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class ManagmentApplicationService(val restTemplate: RestTemplate) {

    val logger: Logger = LoggerFactory.getLogger(ManagmentApplicationService::class.java)

}