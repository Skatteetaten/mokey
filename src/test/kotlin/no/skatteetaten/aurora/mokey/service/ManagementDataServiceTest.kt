package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import no.skatteetaten.aurora.mokey.model.Endpoint
import no.skatteetaten.aurora.mokey.model.HealthResponse
import no.skatteetaten.aurora.mokey.model.HealthStatus
import no.skatteetaten.aurora.mokey.model.HttpResponse
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementLinks
import no.skatteetaten.aurora.mokey.model.ManagementEndpointError
import no.skatteetaten.aurora.utils.Left
import no.skatteetaten.aurora.utils.Right
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ManagementDataServiceTest {
    private val managementEndpointFactory = mockk<ManagementEndpointFactory>()
    private val managementEndpoint = mockk<ManagementEndpoint>()
    private val managementEndpointError = mockk<ManagementEndpointError>()
    private val managementDataService = ManagementDataService(managementEndpointFactory)

    @BeforeEach
    fun setUp() {
        clearMocks(managementEndpointFactory, managementEndpoint)
    }

    @Test
    fun `Load management endpoint return error when host address is empty`() {
        val result = managementDataService.load("", "/test")
        assert((result as Left).value.message).isEqualTo("Host address is missing")
    }

    @Test
    fun `Load management endpoint return error when endpointPath is empty`() {
        val result = managementDataService.load("http://localhost", "")
        assert((result as Left).value.message).isEqualTo("Management Path is missing")
    }

    @Test
    fun `Load management endpoint returns Right even if health and info return Left`() {
        every { managementEndpoint.getInfoEndpointResponse() } returns Left(managementEndpointError)
        every { managementEndpoint.getHealthEndpointResponse() } returns Left(managementEndpointError)
        every { managementEndpoint.links } returns mockk<ManagementLinks>()
        every { managementEndpointFactory.create(any()) } returns managementEndpoint
        val result = managementDataService.load("http://localhost", "/test")
        assert((result as Right).value.links).isNotNull()
    }

    @Test
    fun `Load returns Left if unable to create endpoints`() {
        every { managementEndpointFactory.create(any()) } throws ManagementEndpointException(Endpoint.MANAGEMENT, "")
        val result = managementDataService.load("http://localhost", "/test")
        assert((result as Left).value.message).isEqualTo("Error while communicating with management endpoint")
    }

    @Test
    fun `Load management endpoint returns Right on happy day`() {
        every { managementEndpoint.getInfoEndpointResponse() } returns Right(HttpResponse(InfoResponse(), ""))
        every { managementEndpoint.getHealthEndpointResponse() } returns Right(HttpResponse(HealthResponse(status = HealthStatus.UP), ""))
        every { managementEndpoint.links } returns ManagementLinks(emptyMap())
        every { managementEndpointFactory.create(any()) } returns managementEndpoint

        val result = managementDataService.load("http://localhost", "test")
        assert((result as Right).value.links).isNotNull()
    }
}