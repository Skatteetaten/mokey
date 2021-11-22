package no.skatteetaten.aurora.mokey.extensions

fun <T> List<T>.addIfNotNull(value: T?): List<T> = value?.let {
    this + it
} ?: this

fun <T> List<T>.addIfNotNull(value: List<T>?): List<T> = value?.let {
    this + it
} ?: this
