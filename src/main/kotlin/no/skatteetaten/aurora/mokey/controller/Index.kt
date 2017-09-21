package no.skatteetaten.aurora.mokey.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(value = "/")
class IndexController {

    @GetMapping("/")
    fun index(): String {
        return "redirect:/docs/index.html"
    }
}
