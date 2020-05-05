package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.skatteetaten.aurora.mokey.model.DiscoveryResponse
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import org.intellij.lang.annotations.Language

class ManagementDataBuilder(

    @Language("JSON")
    val infoResponseJson: String = """{
  "git": {
    "build.time": "2018-01-01 00:00:01Z",
    "commit.time": "2018-01-01 00:00:01Z",
    "commit.id.abbrev": ""
  },
 "podLinks": {
    "metrics": "http://localhost"
  }
}""",

    @Language("JSON")
    val healthResponseJson: String = """{"status": "UP"}""",

    @Language("JSON")
    val linksResponseJson: String = """{
  "_links": {
      "self": {
          "href": "http://localhost:8081/actuator"
        },
      "health": {
          "href": "http://localhost:8081/health"
        },
      "info": {
          "href": "http://localhost:8081/info"
        },
      "env": {
          "href": "http://localhost:8081/env"
      }
    }
  }"""
) {
    private val info: ManagementEndpointResult<InfoResponse> = ManagementEndpointResult(
        deserialized = jacksonObjectMapper().readValue<InfoResponse>(infoResponseJson),
        response = HttpResponse(content = infoResponseJson, code = 200),
        resultCode = "OK",
        url = "http://localhost:8081/info",
        endpointType = EndpointType.INFO
    )

    private val links: ManagementEndpointResult<DiscoveryResponse> = ManagementEndpointResult(
        deserialized = jacksonObjectMapper().readValue<DiscoveryResponse>(linksResponseJson),
        response = HttpResponse(content = linksResponseJson, code = 200),
        resultCode = "OK",
        url = "http://localhost:8081/actuator",
        endpointType = EndpointType.DISCOVERY
    )

    fun build() = ManagementData(
        links,
        info = info,
        health = null
    )
}
