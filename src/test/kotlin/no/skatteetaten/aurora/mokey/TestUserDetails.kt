package no.skatteetaten.aurora.mokey

import no.skatteetaten.aurora.mokey.controller.security.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Component

@Component
class TestUserDetails : UserDetailsService {
    override fun loadUserByUsername(username: String?): UserDetails {
        return User("username", "token", "fullName")
    }
}