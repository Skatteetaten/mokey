package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.DeploymentConfigDataBuilder
import no.skatteetaten.aurora.mokey.RouteBuilder
import no.skatteetaten.aurora.mokey.ServiceBuilder
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_MARJORY_DONE
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_MARJORY_OPEN
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_MARJORY_SERVICE
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_WEMBLEY_DONE
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_WEMBLEY_EXTERNAL_HOST
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_WEMBLEY_PATHS
import no.skatteetaten.aurora.mokey.extensions.ANNOTATION_WEMBLEY_SERVICE
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter

class AddressServiceTest {

    private val openShiftService = mockk<OpenShiftService>()
    private val addressService = AddressService(openShiftService)

    val dcBuilder = DeploymentConfigDataBuilder()
    @BeforeEach
    fun setUp() {
        clearMocks(openShiftService)
    }

    @Test
    fun `should collect service address`() {
        val serviceBuilder = ServiceBuilder()
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(serviceBuilder.build())
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf()
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName))
        assert(addresses).hasSize(1)
        assert(addresses[0].url).isEqualTo(URI.create("http://${serviceBuilder.serviceName}"))
        assert(addresses[0].time).isEqualTo(Instant.EPOCH)
        assert(addresses[0].available).isTrue()

    }

    @Test
    fun `should collect service and route address`() {
        val serviceBuilder = ServiceBuilder()
        val routeBuilder = RouteBuilder()
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(serviceBuilder.build())
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(routeBuilder.build())
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName))
        assert(addresses).hasSize(2)
        assert(addresses[1].url).isEqualTo(URI.create("http://${routeBuilder.routeHost}"))
        assert(addresses[1].time).isEqualTo(Instant.EPOCH)
        assert(addresses[1].available).isTrue()

    }


    @Test
    fun `should collect service and path based route address`() {
        val serviceBuilder = ServiceBuilder()
        val routeBuilder = RouteBuilder(routePath = "foo")
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(serviceBuilder.build())
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(routeBuilder.build())
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName))
        assert(addresses).hasSize(2)
        assert(addresses[1].url).isEqualTo(URI.create("http://${routeBuilder.routeHost}/foo"))
        assert(addresses[1].time).isEqualTo(Instant.EPOCH)
        assert(addresses[1].available).isTrue()

    }

    @Test
    fun `should collect service, route and bigip address`() {
        val serviceBuilder = ServiceBuilder()
        val routeBuilder = RouteBuilder(routeAnnotations = mapOf(
                ANNOTATION_WEMBLEY_SERVICE to dcBuilder.dcName,
                ANNOTATION_WEMBLEY_DONE to "2018-01-25 10:40:49.322904761 +0100 CET", //I do not want to spend more time trying to parse this stupid format.
                ANNOTATION_WEMBLEY_EXTERNAL_HOST to "skatt-utv3.sits.no",
                ANNOTATION_WEMBLEY_PATHS to "/web/foo/,/api/foo/"
        ))
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(serviceBuilder.build())
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(routeBuilder.build())
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName))
        assert(addresses).hasSize(3)
        assert(addresses[2].url).isEqualTo(URI.create("https://skatt-utv3.sits.no/app-name"))
        assert(addresses[2].time).isEqualTo(Instant.EPOCH)
        assert(addresses[2].available).isTrue()

    }

    @Test
    fun `should collect service, route and webseal address`() {
        val time = "2017-11-14T12:50:23.864+01:00"
        val serviceBuilder = ServiceBuilder(serviceAnnotations = mapOf(
                ANNOTATION_MARJORY_DONE to time
        ))

        val routeBuilder = RouteBuilder(routeHost = "${dcBuilder.dcName}.amutv.skead.no", routeAnnotations = mapOf(
                ANNOTATION_MARJORY_SERVICE to dcBuilder.dcName,
                ANNOTATION_MARJORY_DONE to time,
                ANNOTATION_MARJORY_OPEN to "true"
        ))
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf(serviceBuilder.build())
        every { openShiftService.route(dcBuilder.dcNamespace, "${dcBuilder.dcName}-webseal") } returns routeBuilder.build()
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName)) } returns listOf()
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, mapOf("name" to dcBuilder.dcName))
        assert(addresses).hasSize(2)
        assert(addresses[1].url).isEqualTo(URI.create("https://app-name.amutv.skead.no"))
        assert(addresses[1].time).isEqualTo(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(time, Instant::from))
        assert(addresses[1].available).isTrue()

    }
}