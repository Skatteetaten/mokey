package no.skatteetaten.aurora.mokey

import static com.fasterxml.jackson.module.kotlin.ExtensionsKt.jacksonObjectMapper


import no.skatteetaten.aurora.mokey.model.AuroraApplication
import spock.lang.Specification

abstract class AbstractSpec extends Specification {

  def mapper = jacksonObjectMapper()

  String loadResource(String resourceName) {
    def folder = this.getClass().simpleName
    loadResource(folder, resourceName)
  }

  AuroraApplication loadApplication(String name) {
    return  mapper.readValue(loadResource(name), AuroraApplication)
  }

  String loadResource(String folder, String resourceName) {
    def resourcePath = "${folder}/$resourceName"
    this.getClass().getResource(resourcePath)?.text ?: { throw new IllegalArgumentException("No such resource $resourcePath")}()
  }
}