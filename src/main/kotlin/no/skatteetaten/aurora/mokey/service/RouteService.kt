package no.skatteetaten.aurora.mokey.service

import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

data class Route(val namespace: String, val name: String)

@Component
class RouteService(val restTemplate: RestTemplate) {

    fun findRoute(namespace: String, name: String): Route? {
        return Route(namespace, name)
    }
}