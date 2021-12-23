package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.springboot.webclient.extensions.kotlin.createWebTestClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.context.ApplicationContext
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.test.web.reactive.server.WebTestClient

@ExtendWith(RestDocumentationExtension::class)
abstract class TestStubSetup {

    protected lateinit var webTestClient: WebTestClient

    @BeforeEach
    fun setUp(applicationContext: ApplicationContext, restDocumentation: RestDocumentationContextProvider) {
        webTestClient = applicationContext.createWebTestClient(restDocumentation)
    }
}
