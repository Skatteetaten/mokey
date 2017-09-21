package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.service.Route
import no.skatteetaten.aurora.mokey.service.RouteService
import org.springframework.hateoas.ResourceSupport
import org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo
import org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn
import org.springframework.http.HttpEntity
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

class RouteResource(val route: Route) : ResourceSupport() {
    init {
        add(linkTo(methodOn(OpenShiftRouteController::class.java).route(route.namespace, route.routeName)).withSelfRel())
    }
}

@RestController
@RequestMapping(value = "/route", produces = arrayOf("application/hal+json"))
class OpenShiftRouteController(val routeService: RouteService) {

    @GetMapping("/{namespace}/{routeName}")
    fun route(@PathVariable namespace: String, @PathVariable routeName: String): HttpEntity<RouteResource> {

        val route = routeService.findRoute(namespace, routeName)
        return ResponseEntity(RouteResource(route), HttpStatus.OK)
    }
}