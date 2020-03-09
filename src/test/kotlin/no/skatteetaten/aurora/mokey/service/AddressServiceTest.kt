package no.skatteetaten.aurora.mokey.service

/*
 comment out on EKS for now
class AddressServiceTest {

    private val client = mockk<OpenShiftServiceAccountClient>()
    private val addressService = AddressService(client)

    val dcBuilder = DeploymentConfigDataBuilder()
    @BeforeEach
    fun setUp() {
        clearMocks(client)
    }

    @Test
    fun `should collect service address`() {
        runBlocking {

            val meta = newObjectMeta {
                namespace = dcBuilder.dcNamespace
                labels = mapOf("app" to dcBuilder.dcName)
            }

            val serviceBuilder = ServiceBuilder()
            coEvery { client.getServices(any()) } returns listOf(serviceBuilder.build())

            coEvery { client.getIngresses(any()) } returns listOf()

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
    }

    @Test
    fun `should collect service and route address for secured route`() {
        runBlocking {
            val serviceBuilder = ServiceBuilder()
            val routeBuilder = RouteBuilder(tlsEnabled = true)
            coEvery { client.getServices(any()) } returns listOf(serviceBuilder.build())
            coEvery { client.getIngresses(any()) } returns listOf(routeBuilder.build())

            val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
            assertThat(addresses).hasSize(2)
            assertThat(addresses[1]).isEqualTo(
                url = "https://${routeBuilder.routeHost}",
                time = Instant.EPOCH
            )
        }
    }

    @Test
    fun `should collect service and route address`() {
        runBlocking {
            val serviceBuilder = ServiceBuilder()
            val routeBuilder = RouteBuilder()

            coEvery { client.getServices(any()) } returns listOf(serviceBuilder.build())
            coEvery { client.getRoutes(any()) } returns listOf(routeBuilder.build())
            val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
            assertThat(addresses).hasSize(2)
            assertThat(addresses[1]).isEqualTo(
                url = "http://${routeBuilder.routeHost}",
                time = Instant.EPOCH
            )
        }
    }

    @Test
    fun `should collect service and path based route address`() {
        runBlocking {
            val serviceBuilder = ServiceBuilder()
            val routeBuilder = RouteBuilder(routePath = "foo")

            coEvery { client.getServices(any()) } returns listOf(serviceBuilder.build())
            coEvery { client.getRoutes(any()) } returns listOf(routeBuilder.build())

            val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
            assertThat(addresses).hasSize(2)
            assertThat(addresses[1]).isEqualTo(
                url = "http://${routeBuilder.routeHost}/foo",
                time = Instant.EPOCH
            )
        }
    }

    @Test
    fun `should collect service, route and bigip address`() {
        runBlocking {
            val serviceBuilder = ServiceBuilder()
            val routeBuilder = RouteBuilder(
                routeAnnotations = mapOf(
                    ANNOTATION_WEMBLEY_SERVICE to dcBuilder.dcName,
                    ANNOTATION_WEMBLEY_DONE to "2018-01-25 10:40:49.322904761 +0100 CET", // I do not want to spend more time trying to parse this stupid format.
                    ANNOTATION_WEMBLEY_EXTERNAL_HOST to "skatt-utv3.sits.no",
                    ANNOTATION_WEMBLEY_PATHS to "/web/foo/,/api/foo/"
                )
            )

            coEvery { client.getRoutes(any()) } returns listOf(routeBuilder.build())
            coEvery { client.getServices(any()) } returns listOf(serviceBuilder.build())

            val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
            assertThat(addresses).hasSize(3)
            assertThat(addresses[2]).isEqualTo(
                url = "https://skatt-utv3.sits.no/app-name",
                time = Instant.EPOCH
            )
        }
    }

    @Test
    fun `should collect service, route and webseal address`() {
        runBlocking {
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

            coEvery { client.getRoutes(any()) } returns listOf()
            coEvery { client.getServices(any()) } returns listOf(serviceBuilder.build())

            coEvery {
                client.getRouteOrNull(

                    newRoute {
                        metadata {
                            namespace = dcBuilder.dcNamespace
                            name = "${dcBuilder.dcName}-webseal"
                        }
                    }
                )
            } returns routeBuilder.build()

            val addresses = addressService.getAddresses(dcBuilder.dcNamespace, dcBuilder.dcName)
            assertThat(addresses).hasSize(2)
            assertThat(addresses[1]).isEqualTo(
                url = "https://app-name.amutv.skead.no",
                time = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(time, Instant::from)
            )
        }
    }
}

fun Assert<Address>.isEqualTo(url: String, time: Instant) {
    prop("available", Address::available).isTrue()
    prop("url", Address::url).isEqualTo(URI.create(url))
    prop("time", Address::time).isEqualTo(time)
}
 */
