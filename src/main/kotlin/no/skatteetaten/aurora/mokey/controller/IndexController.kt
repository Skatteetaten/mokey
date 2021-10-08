package no.skatteetaten.aurora.mokey.controller

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/")
class IndexController {
    @GetMapping
    fun index(): String = "redirect:/docs/index.html"
}
