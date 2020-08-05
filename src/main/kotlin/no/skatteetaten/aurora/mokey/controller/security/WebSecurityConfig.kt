package no.skatteetaten.aurora.mokey.controller.security

import io.fabric8.kubernetes.api.model.authentication.TokenReview
import javax.servlet.http.HttpServletRequest
import mu.KotlinLogging
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter
import org.springframework.security.web.util.matcher.RequestMatcher

private val logger = KotlinLogging.logger {}

@EnableWebSecurity
class WebSecurityConfig(
    val authenticationManager: BearerAuthenticationManager,
    @Value("\${management.server.port}") val managementPort: Int
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {

        http.csrf().disable()
        http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        http.authenticationProvider(preAuthenticationProvider())
            .addFilter(requestHeaderAuthenticationFilter())
            .authorizeRequests()
            .requestMatchers(forPort(managementPort)).permitAll()
            .antMatchers("/api/auth/**").authenticated()
            .anyRequest().permitAll()
    }

    private fun forPort(port: Int) = RequestMatcher { request: HttpServletRequest -> port == request.localPort }

    @Bean
    internal fun preAuthenticationProvider() = PreAuthenticatedAuthenticationProvider().apply {
        setPreAuthenticatedUserDetailsService { it: PreAuthenticatedAuthenticationToken ->

            val principal = it.principal as TokenReview
            val fullName = principal.status.user.username
            val username = principal.status.user.username

            MDC.put("user", username)
            User(username, it.credentials as String, fullName).also {
                logger.debug("Logged in user username=$username, name='$fullName' tokenSnippet=${it.tokenSnippet}")
            }
        }
    }

    @Bean
    internal fun requestHeaderAuthenticationFilter() = RequestHeaderAuthenticationFilter().apply {
        setPrincipalRequestHeader("Authorization")
        setExceptionIfHeaderMissing(false)
        setAuthenticationManager(authenticationManager)
    }
}
