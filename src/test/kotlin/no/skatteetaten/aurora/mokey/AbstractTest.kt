package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.skatteetaten.aurora.mokey.model.ApplicationData

open class AbstractTest {
    fun loadResource(resourceName: String): String {
        val folder = this.javaClass.simpleName
        return loadResource(folder, resourceName)
    }

    fun loadApplication(name: String): ApplicationData {
        return jacksonObjectMapper().readValue(loadResource(name))
    }

    fun loadResource(folder: String, resourceName: String): String {
        val resourcePath = "$folder/$resourceName"
        return this.javaClass.getResource(resourcePath)?.readText()
                ?: { throw IllegalArgumentException("No such resource $resourcePath") }()
    }
}