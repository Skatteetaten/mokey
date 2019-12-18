package no.skatteetaten.aurora.mokey.controller.security

import java.util.regex.Pattern
import no.skatteetaten.aurora.mokey.service.OpenShiftService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException

@Component
class BearerAuthenticationManager(private val openShiftService: OpenShiftService) : AuthenticationManager {

    companion object {
        private val headerPattern: Pattern = Pattern.compile("Bearer\\s+(.*)", Pattern.CASE_INSENSITIVE)

        private fun getBearerTokenFromAuthentication(authentication: Authentication?): String {
            val authenticationHeaderValue = authentication?.principal?.toString()
            val matcher = headerPattern.matcher(authenticationHeaderValue)
            if (!matcher.find()) {
                throw BadCredentialsException("Unexpected Authorization header format")
            }
            return matcher.group(1)
        }
    }

    override fun authenticate(authentication: Authentication?): Authentication {
        try {
            val token = getBearerTokenFromAuthentication(authentication)
            val user = openShiftService.user(token)
            return PreAuthenticatedAuthenticationToken(user, token)
        } catch (e: WebClientResponseException.Unauthorized) {
            throw BadCredentialsException(e.localizedMessage, e)
        }
    }
}
