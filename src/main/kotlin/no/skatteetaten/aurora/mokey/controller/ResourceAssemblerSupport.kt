package no.skatteetaten.aurora.mokey.controller

abstract class ResourceAssemblerSupport<T, D> {

    abstract fun toResource(entity: T): D

    fun toResources(entities: Iterable<T>) = entities.map { toResource(it) }
}