package no.skatteetaten.aurora.mokey

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.ManagementEndpointResult
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import org.intellij.lang.annotations.Language
import java.time.Instant

data class ManagementEndpointResultDataBuilder<T>(
    val deserialized: T? = null,
    val textResponse: String = "",
    val code: String = "OK",
    val createdAt: Instant = Instant.now(),
    val endpointType: EndpointType,
    val errorMessage: String? = null,
    val url: String? = null
) {
    fun build(): ManagementEndpointResult<T> =
            ManagementEndpointResult(
                    deserialized = deserialized,
                    response = HttpResponse(content = textResponse, code = 200),
                    createdAt = createdAt,
                    endpointType = endpointType,
                    errorMessage = errorMessage,
                    url = url,
                    resultCode = "OK"
            )
}

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
            deserialized = jacksonObjectMapper().readValue(infoResponseJson, InfoResponse::class.java),
            response = HttpResponse(content = infoResponseJson, code = 200),
            resultCode = "OK",
            url = "http://localhost:8081/info",
            endpointType = EndpointType.INFO
    )

    private val links: ManagementEndpointResult<ManagementLinks> = ManagementEndpointResult(
            deserialized = ManagementLinks.parseManagementResponse(jacksonObjectMapper().readValue(linksResponseJson, JsonNode::class.java)),
            response = HttpResponse(content = linksResponseJson, code = 200),
            resultCode = "OK",
            url = "http://localhost:8081/actuator",
            endpointType = EndpointType.DISCOVERY
    )

    fun build() = ManagementData(
            links = links,
            info = info,
        health = null
    )
}
