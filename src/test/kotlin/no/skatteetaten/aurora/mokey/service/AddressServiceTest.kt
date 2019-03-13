package no.skatteetaten.aurora.mokey.service

import assertk.Assert
import assertk.assertThat
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
import no.skatteetaten.aurora.mokey.model.Address
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
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            serviceBuilder.build()
        )
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf()
        val addresses = addressService.getAddresses(
            dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)["app"]
                ?: ""
        )
        assertThat(addresses).hasSize(1)
        assertThat(addresses[0]).isEqualTo(
            url = "http://${serviceBuilder.serviceName}",
            time = Instant.EPOCH
        )
    }

    @Test
    fun `should collect service and route address`() {
        val serviceBuilder = ServiceBuilder()
        val routeBuilder = RouteBuilder()
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            serviceBuilder.build()
        )
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            routeBuilder.build()
        )
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
        assertThat(addresses).hasSize(2)
        assertThat(addresses[1]).isEqualTo(
            url = "http://${routeBuilder.routeHost}",
            time = Instant.EPOCH
        )
    }

    @Test
    fun `should collect service and path based route address`() {
        val serviceBuilder = ServiceBuilder()
        val routeBuilder = RouteBuilder(routePath = "foo")
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            serviceBuilder.build()
        )
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            routeBuilder.build()
        )
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
        assertThat(addresses).hasSize(2)
        assertThat(addresses[1]).isEqualTo(
            url = "http://${routeBuilder.routeHost}/foo",
            time = Instant.EPOCH
        )
    }

    @Test
    fun `should collect service, route and bigip address`() {
        val serviceBuilder = ServiceBuilder()
        val routeBuilder = RouteBuilder(
            routeAnnotations = mapOf(
                ANNOTATION_WEMBLEY_SERVICE to dcBuilder.dcName,
                ANNOTATION_WEMBLEY_DONE to "2018-01-25 10:40:49.322904761 +0100 CET", // I do not want to spend more time trying to parse this stupid format.
                ANNOTATION_WEMBLEY_EXTERNAL_HOST to "skatt-utv3.sits.no",
                ANNOTATION_WEMBLEY_PATHS to "/web/foo/,/api/foo/"
            )
        )
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            serviceBuilder.build()
        )
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            routeBuilder.build()
        )
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
        assertThat(addresses).hasSize(3)
        assertThat(addresses[2]).isEqualTo(
            url = "https://skatt-utv3.sits.no/app-name",
            time = Instant.EPOCH
        )
    }

    @Test
    fun `should collect service, route and webseal address`() {
        val time = "2017-11-14T12:50:23.864+01:00"
        val serviceBuilder = ServiceBuilder(
            serviceAnnotations = mapOf(
                ANNOTATION_MARJORY_DONE to time
            )
        )

        val routeBuilder = RouteBuilder(
            routeHost = "${dcBuilder.dcName}.amutv.skead.no", routeAnnotations = mapOf(
                ANNOTATION_MARJORY_SERVICE to dcBuilder.dcName,
                ANNOTATION_MARJORY_DONE to time,
                ANNOTATION_MARJORY_OPEN to "true"
            )
        )
        every { openShiftService.services(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf(
            serviceBuilder.build()
        )
        every {
            openShiftService.route(
                dcBuilder.dcNamespace,
                "${dcBuilder.dcName}-webseal"
            )
        } returns routeBuilder.build()
        every { openShiftService.routes(dcBuilder.dcNamespace, mapOf("app" to dcBuilder.dcName)) } returns listOf()
        val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
        assertThat(addresses).hasSize(2)
        assertThat(addresses[1]).isEqualTo(
            url = "https://app-name.amutv.skead.no",
            time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(time, Instant::from)
        )
    }
}

fun Assert<Address>.isEqualTo(url: String, time: Instant) {
    // TODO: fix this
    assertThat(actual.available).isTrue()
    assertThat(actual.url).isEqualTo(URI.create(url))
    assertThat(actual.time).isEqualTo(time)
}