package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.skatteetaten.aurora.mockmvc.extensions.TestObjectMapperConfigurer
import no.skatteetaten.aurora.mokey.controller.security.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.restdocs.AutoConfigureRestDocs
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component
import org.springframework.test.web.servlet.MockMvc

@AutoConfigureRestDocs
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestUserDetailsService::class)
class AbstractSecurityControllerTest : AbstractTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        TestObjectMapperConfigurer.objectMapper = configureObjectMapper(objectMapper)
    }

    @AfterEach
    fun tearDown() {
        TestObjectMapperConfigurer.reset()
    }
}

@Component
private class TestUserDetailsService : UserDetailsService {
    override fun loadUserByUsername(username: String?): UserDetails {
        return User(username ?: "username", "token", "fullName")
    }
}

// TOOD: Do we need this?
fun configureObjectMapper(objectMapper: ObjectMapper): ObjectMapper {
    return objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .registerModules(JavaTimeModule())
        .registerKotlinModule()
}
