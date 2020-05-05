package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.controller.security.User
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
class AbstractSecurityControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc
}

@Component
private class TestUserDetailsService : UserDetailsService {
    override fun loadUserByUsername(username: String?): UserDetails {
        return User(username ?: "username", "token", "fullName")
    }
}
