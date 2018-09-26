package no.skatteetaten.aurora.mokey.extensions

import io.fabric8.kubernetes.client.dsl.Gettable

fun <T> Gettable<T>.getOrNull(): T? {
    return this.get() ?: return null
}
