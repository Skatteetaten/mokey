package no.skatteetaten.aurora.mokey.controller.internal;

import no.skatteetaten.aurora.mokey.service.NoAccessException
import no.skatteetaten.aurora.mokey.service.NoSuchResourceException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.lang.Exception

@ControllerAdvice
class ErrorHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler(RuntimeException::class)
    fun handleGenericError(e: RuntimeException, request: WebRequest): ResponseEntity<Any>? {
        return handleException(e, request, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(NoAccessException::class)
    fun handleNoAccess(e: NoAccessException, request: WebRequest): ResponseEntity<Any>? {
        return handleException(e, request, HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(NoSuchResourceException::class)
    fun handleResourceNotFound(e: NoSuchResourceException, request: WebRequest): ResponseEntity<Any>? {
        return handleException(e, request, HttpStatus.NOT_FOUND)
    }

    private fun handleException(e: Exception, request: WebRequest, httpStatus: HttpStatus): ResponseEntity<Any>? {
        val response = mutableMapOf(Pair("errorMessage", e.message))
        e.cause?.apply { response.put("cause", this.message) }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        logger.debug("Handle excption", e)
        return handleExceptionInternal(e, response, headers, httpStatus, request)
    }

}
