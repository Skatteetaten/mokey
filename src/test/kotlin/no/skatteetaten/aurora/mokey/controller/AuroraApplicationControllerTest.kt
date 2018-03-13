package no.skatteetaten.aurora.mokey.controller

import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.AbstractSecurityControllerTest
import no.skatteetaten.aurora.mokey.TestUserDetails
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.test.context.support.WithUserDetails
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@WebMvcTest(
        TestUserDetails::class,
        Config::class,
        AuroraApplicationController::class
)
class AuroraApplicationControllerTest : AbstractSecurityControllerTest() {

    private val NAMESPACE = "aurora"
    private val APP = "reference"

    @Autowired
    lateinit var openShiftService: OpenShiftService

    @Autowired
    lateinit var cacheService: AuroraApplicationCacheService

    @Test
    @WithUserDetails
    fun `Get AuroraApplication given user with access`() {
        every { cacheService.get("aurora/reference") } returns loadApplication("app.json")
        every { openShiftService.currentUserHasAccess(NAMESPACE) } returns true

        mockMvc.perform(get("/aurora/namespace/{namespace}/application/{name}", NAMESPACE, APP))
                .andExpect(status().isOk)
    }
}

@Configuration
private class Config {
    @Bean
    fun restTemplateBuilder(): RestTemplateBuilder = RestTemplateBuilder()

    @Bean
    fun auroraApplicationCacheService(): AuroraApplicationCacheService = mockk()

    @Bean
    fun openShiftService(): OpenShiftService = mockk()
}