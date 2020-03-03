package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fkorotkov.kubernetes.newPod
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.skatteetaten.aurora.mokey.ManagementEndpointResultDataBuilder
import no.skatteetaten.aurora.mokey.model.EndpointType
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManagementDataServiceTest {
    private val managementInterfaceFactory = mockk<ManagementInterfaceFactory>()
    private val managementInterface = mockk<ManagementInterface>()
    private val managementDataService = ManagementDataService(managementInterfaceFactory)

    private val discoveryResponse = """{
  "_links": {
    "health": {
      "href": "http://localhost:8081/health"
    },
    "env": {
      "href": "http://localhost:8081/env"
    },
    "info": {
      "href": "http://localhost:8081/info"
    }
  }
}"""

    private val infoResponse = """{
  "serviceLinks": {
    "api-doc": "myhost/docs/index.html",
    "metrics": "myhost/foo/bar"
  },
  "dependencies": {
    "skatteetaten": "skatteetaten"
  },
  "auroraVersion": "2.0.13-b1.17.0-foo-8.181.1",
  "podLinks": {
    "metrics": "/my/foo/bar/mypod"
  },
  "imageBuildTime": "2018-10-24T17:42:37Z",
  "git": {
    "commit": {
      "time": "2018-10-23T08:34:31Z",
      "id": "ab23ea76"
    },
    "branch": "c72fd243cd418313b1123efdb3e6f82cd9427e58"
  },
  "build": {
    "version": "2.0.13",
    "artifact": "myapp",
    "name": "myapp",
    "group": "no.skatteetaten.aurora.openshift",
    "time": "2018-10-24T17:40:57.509Z"
  }
}"""

    private val healthResponse = """{
  "status": "UP",
  "atsServiceHelse": {
    "status": "UP"
  },
  "diskSpace": {
    "status": "UP",
    "total": 10718543872,
    "free": 10508611584,
    "threshold": 10485760
  },
  "db": {
    "status": "UP",
    "database": "Mydb",
    "hello": "Hello"
  }
}"""

    private val envResponse = """{
  "activeProfiles": [
    "myprofile"
  ],
  "propertySources": [
    {
        "name": "systemProperties",
        "properties": {
            "os.name": {
                "value": "Linux"
            },
            "os.version": {
                "value": "3.12.x86_64"
            },
            "file.encoding.pkg": {
                "value": "sun.io"
            }
        }
    },
    {
        "name": "applicationProperties",
        "properties": {
            "user.level": {
                "value": "3"
            },
            "user.home": {
                "value": "/home/bozo"
            }
        }
    }
  ]
}"""

    @BeforeEach
    fun setUp() {
        clearMocks(managementInterfaceFactory, managementInterface)
    }

    @Test
    fun `Return discovery result if unable to create management interface`() {
        val discoveryResult = ManagementEndpointResultDataBuilder<ManagementLinks>(
            textResponse = """{"foo" : "bar" }""",
            endpointType = EndpointType.INFO
        )
            .build()

        every { ManagementInterface.create(managementInterfaceFactory.client, any(), any()) } returns Pair(
            null,
            discoveryResult
        )

        val result = managementDataService.load(newPod {}, "/bar")

        verify { ManagementInterface.create(managementInterfaceFactory.client, any(), any()) }
        assertThat(result.links).isEqualTo(discoveryResult)
        assertThat(result.health).isNull()
        assertThat(result.info).isNull()
        assertThat(result.env).isNull()
    }

    @Test
    fun `Return aggregated result object on happy day`() {
        val infoResult = ManagementEndpointResultDataBuilder(
            deserialized = jacksonObjectMapper().readValue(infoResponse, InfoResponse::class.java),
            endpointType = EndpointType.INFO
        )
            .build()

        val healthResult = ManagementEndpointResultDataBuilder(
            deserialized = HealthResponseParser.parse(
                jacksonObjectMapper().readValue(
                    healthResponse,
                    JsonNode::class.java
                )
            ),
            endpointType = EndpointType.HEALTH
        )
            .build()

        val discoveryResult = ManagementEndpointResultDataBuilder(
            deserialized = ManagementLinks.parseManagementResponse(
                jacksonObjectMapper().readValue(
                    discoveryResponse,
                    JsonNode::class.java
                )
            ),
            endpointType = EndpointType.DISCOVERY
        )
            .build()

        val envResult = ManagementEndpointResultDataBuilder(
            deserialized = jacksonObjectMapper().readValue(envResponse, JsonNode::class.java),
            endpointType = EndpointType.ENV
        )
            .build()

        every { managementInterface.getInfoEndpointResult() } returns infoResult
        every { managementInterface.getEnvEndpointResult() } returns envResult
        every { managementInterface.getHealthEndpointResult() } returns healthResult
        every { ManagementInterface.create(managementInterfaceFactory.client, any(), any()) } returns Pair(
            managementInterface,
            discoveryResult
        )

        val data = managementDataService.load(newPod {}, "/test")

        verify { ManagementInterface.create(managementInterfaceFactory.client, any(), any()) }
        verify { managementInterface.getInfoEndpointResult() }
        verify { managementInterface.getEnvEndpointResult() }
        verify { managementInterface.getHealthEndpointResult() }

        assertThat(data.links.endpointType).isEqualTo(EndpointType.DISCOVERY)
        assertThat(data.info!!.endpointType).isEqualTo(EndpointType.INFO)
        assertThat(data.env!!.endpointType).isEqualTo(EndpointType.ENV)
        assertThat(data.health!!.endpointType).isEqualTo(EndpointType.HEALTH)
    }
}
