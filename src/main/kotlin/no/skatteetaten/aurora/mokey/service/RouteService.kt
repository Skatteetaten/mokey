package no.skatteetaten.aurora.mokey.service

import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

data class Route(val namespace: String, val routeName: String)

@Component
class RouteService(val restTemplate: RestTemplate) {

    fun findRoute(namespace: String, routeName: String): Route {
        return Route(namespace, routeName)
    }
}