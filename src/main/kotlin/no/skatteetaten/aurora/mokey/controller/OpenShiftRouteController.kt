package no.skatteetaten.aurora.mokey.controller

import io.fabric8.openshift.client.OpenShiftClient
import no.skatteetaten.aurora.mokey.extensions.getOrNull
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

data class Route(val namespace: String, val name:String)
class RouteResource(val route: Route) : ResourceSupport() {
    init {
        add(linkTo(methodOn(OpenShiftRouteController::class.java).route(route.namespace, route.name)).withSelfRel())
    }
}

@RestController
@RequestMapping(value = "/api/route", produces = arrayOf("application/hal+json"))
class OpenShiftRouteController(val openShiftClient: OpenShiftClient) {

    @GetMapping("/{namespace}/{name}")
    fun route(@PathVariable namespace: String, @PathVariable name: String): HttpEntity<RouteResource> {

        val route = openShiftClient.routes().inNamespace(namespace).withName(name).getOrNull()?.let {
            Route(namespace, name)
        } ?: throw NoSuchResourceException("No route named $name in namespace $namespace")
        return ResponseEntity(RouteResource(route), HttpStatus.OK)
    }
}

