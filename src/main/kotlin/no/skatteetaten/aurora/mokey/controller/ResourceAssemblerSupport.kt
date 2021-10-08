package no.skatteetaten.aurora.mokey.controller

abstract class ResourceAssemblerSupport<T, D> {
    @ExperimentalStdlibApi
    abstract fun toResource(entity: T): D

    @ExperimentalStdlibApi
    fun toResources(entities: Iterable<T>) = entities.map { toResource(it) }
}
