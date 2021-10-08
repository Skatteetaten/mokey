package no.skatteetaten.aurora.mokey.controller

import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.service.NoAccessException
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebExceptionHandler
import reactor.core.publisher.Mono
import reactor.core.publisher.Mono.just

private val logger = KotlinLogging.logger { }

@Suppress("unused")
@Component
@Order(-2)
class ErrorHandler : WebExceptionHandler {
    override fun handle(exchange: ServerWebExchange, ex: Throwable): Mono<Void> = when (ex) {
        is ResponseStatusException -> handleResponseStatus(ex, exchange)
        is IllegalArgumentException -> handleIllegalArgument(ex, exchange)
        is NoSuchResourceException -> handleResourceNotFound(ex, exchange)
        is NoAccessException -> handleNoAccess(ex, exchange)
        else -> handleGenericError(ex, exchange)
    }

    fun handleGenericError(
        e: Throwable,
        exchange: ServerWebExchange
    ): Mono<Void> = handleException(
        e,
        exchange,
        e.message ?: e.localizedMessage,
        INTERNAL_SERVER_ERROR
    )

    private fun handleResponseStatus(
        e: ResponseStatusException,
        exchange: ServerWebExchange
    ): Mono<Void> = handleException(
        e,
        exchange,
        e.message,
        e.status
    )

    fun handleNoAccess(
        e: NoAccessException,
        exchange: ServerWebExchange
    ): Mono<Void> = handleException(
        e,
        exchange,
        e.message ?: e.localizedMessage,
        UNAUTHORIZED
    )

    fun handleResourceNotFound(
        e: NoSuchResourceException,
        exchange: ServerWebExchange
    ): Mono<Void> = handleException(
        e,
        exchange,
        e.message ?: e.localizedMessage,
        NOT_FOUND
    )

    fun handleIllegalArgument(
        e: IllegalArgumentException,
        exchange: ServerWebExchange
    ): Mono<Void> = handleException(
        e,
        exchange,
        e.message ?: e.localizedMessage,
        BAD_REQUEST
    )

    @Suppress("SameParameterValue")
    private fun handleException(
        e: Throwable,
        exchange: ServerWebExchange,
        error: String,
        status: HttpStatus = INTERNAL_SERVER_ERROR
    ): Mono<Void> {
        logger.error(e) { "Error in request" }

        exchange.response.headers.putAll(standardHeaders())
        exchange.response.statusCode = status

        val buffer = exchange.response.bufferFactory().wrap(error.toByteArray())

        return exchange.response.writeWith(just(buffer))
    }

    private fun standardHeaders(): HttpHeaders = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
    }
}
