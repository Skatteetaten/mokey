package no.skatteetaten.aurora.mokey.service

open class ServiceException(message: String? = null, cause: Throwable? = null) : Exception(message, cause)

class TimeoutException(message: String) : ServiceException(message)

class NoAccessException(message: String) : ServiceException(message)
open class OpenShiftException(message: String? = null, cause: Throwable? = null) : ServiceException(message, cause)
class OpenShiftObjectException(message: String? = null, cause: Throwable? = null) : OpenShiftException(message, cause)
