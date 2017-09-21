package no.skatteetaten.aurora.mokey.controller;

import io.micrometer.spring.web.ControllerMetrics
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
class ErrorHandler(val metrics: ControllerMetrics) : ResponseEntityExceptionHandler() {

    @ExceptionHandler(RuntimeException::class)
    fun handleGenericError(e: RuntimeException, request: WebRequest): ResponseEntity<Any>? {
        return handleException(e, request, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    @ExceptionHandler(NoSuchResourceException::class)
    fun handleResourceNotFound(e: NoSuchResourceException, request: WebRequest): ResponseEntity<Any>? {
        return handleException(e, request, HttpStatus.NOT_FOUND)
    }

    private fun handleException(e: Exception, request: WebRequest, httpStatus: HttpStatus): ResponseEntity<Any>? {
        val response = mutableMapOf(Pair("errorMessage", e.message))
        e.cause?.apply { response.put("cause", this.message) }
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return handleExceptionInternal(e, response, headers, httpStatus, request)
    }

/*
    @ExceptionHandler({ RuntimeException.class })
    protected ResponseEntity<Object> handleGenericError(RuntimeException e, WebRequest request) {
        return handleException(e, request, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler({ IllegalArgumentException.class })
    protected ResponseEntity<Object> handleBadRequest(IllegalArgumentException e, WebRequest request) {
        return handleException(e, request, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Object> handleException(final RuntimeException e, WebRequest request,
        HttpStatus httpStatus) {
        metrics.tagWithException(e);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> error = new HashMap<>();
        error.put("errorMessage", e.getMessage());
        if (e.getCause() != null) {
            error.put("cause", e.getCause().getMessage());
        }
        return handleExceptionInternal(e, error, headers, httpStatus, request);
    }
*/
}
