package no.skatteetaten.aurora.mokey

import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

open class AbstractSecurityControllerTest : AbstractTest() {

    @Autowired
    lateinit var webAppContext: WebApplicationContext

    lateinit var mockMvc: MockMvc

    @BeforeAll
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webAppContext).build()
    }
}