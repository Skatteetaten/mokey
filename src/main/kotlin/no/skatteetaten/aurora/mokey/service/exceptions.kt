package no.skatteetaten.aurora.mokey.service

abstract class ServiceException(message: String?, cause: Throwable?) : RuntimeException(message, cause) {
    constructor(message: String) : this(message, null)
}

class OpenShiftException(messages: String?, cause: Throwable?) : ServiceException(messages, cause)

class NoSuchResourceException(message: String) : RuntimeException(message)
class NoAccessException(message: String) : RuntimeException(message)

