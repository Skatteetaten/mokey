package no.skatteetaten.aurora.mokey.controller.security

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.skatteetaten.aurora.kubernetes.ClientTypes
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.TargetClient
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
    @TargetClient(ClientTypes.SERVICE_ACCOUNT) val client: KubernetesCoroutinesClient
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
        try {
            val token = getBearerTokenFromAuthentication(authentication)

            val user = runBlocking {
                client.currentUser(token)
            }

            return PreAuthenticatedAuthenticationToken(user, token)
        } catch (e: WebClientResponseException.Unauthorized) {
            throw BadCredentialsException(e.localizedMessage, e)
        }
    }
}
