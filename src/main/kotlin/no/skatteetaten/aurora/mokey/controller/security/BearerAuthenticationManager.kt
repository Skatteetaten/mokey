package no.skatteetaten.aurora.mokey.controller.security

import kotlinx.coroutines.runBlocking
import no.skatteetaten.aurora.kubernetes.KubernetesCoroutinesClient
import no.skatteetaten.aurora.kubernetes.KubnernetesClientConfiguration
import no.skatteetaten.aurora.kubernetes.RetryConfiguration
import no.skatteetaten.aurora.kubernetes.StringTokenFetcher
import no.skatteetaten.aurora.kubernetes.newCurrentUser
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.security.KeyStore
import java.util.regex.Pattern

@Component
class BearerAuthenticationManager(
    val kubernetesClientConfiguration: KubnernetesClientConfiguration,
    val builder: WebClient.Builder,
    @Qualifier("kubernetesClientWebClient") val trustStore: KeyStore?
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
            val client = KubernetesCoroutinesClient(
                kubernetesClientConfiguration.copy(retry = RetryConfiguration(times = 0))
                    .createUserAccountReactorClient(builder, trustStore, StringTokenFetcher(token))
                    .build()
            )

            val user = runBlocking {
                client.get(newCurrentUser())
            }

            return PreAuthenticatedAuthenticationToken(user, token)
        } catch (e: WebClientResponseException.Unauthorized) {
            throw BadCredentialsException(e.localizedMessage, e)
        }
    }
}
