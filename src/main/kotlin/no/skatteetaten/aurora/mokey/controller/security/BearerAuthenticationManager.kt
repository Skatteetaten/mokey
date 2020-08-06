package no.skatteetaten.aurora.mokey.controller.security

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.service.OpenShiftServiceAccountClient
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.util.regex.Pattern

private val logger = KotlinLogging.logger {}

@Component
class BearerAuthenticationManager(
    val client: OpenShiftServiceAccountClient
) : AuthenticationManager {

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
        logger.info("Start authenticate")
        try {
            val token = getBearerTokenFromAuthentication(authentication)
            logger.info("Fetched token")
            val user = runBlocking {
                client.tokenReview(token)
            }
            logger.info("Logged in with user={}", user)
            return PreAuthenticatedAuthenticationToken(user, token)
        } catch (e: WebClientResponseException.Unauthorized) {
            throw BadCredentialsException(e.localizedMessage, e)
        }
    }
}
