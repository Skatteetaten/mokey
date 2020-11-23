package no.skatteetaten.aurora.mokey.service

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFailure
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ApplicationStatusRegistryTest {

    val data = ApplicationPublicData(
        applicationId = "123-456",
        applicationDeploymentId = "1337",
        applicationName = "reference",
        applicationDeploymentName = "reference",
        auroraStatus = AuroraStatus(AuroraStatusLevel.HEALTHY),
        deployTag = "1",
        auroraVersion = "1.2.3-b4.5.6-wingnut11-7.8.9",
        affiliation = "demo",
        namespace = "demo-test",
        time = Instant.EPOCH,
        environment = "test",
        releaseTo = null
    )

    private lateinit var registry: ApplicationStatusRegistry
    private lateinit var meterRegistry: MeterRegistry
    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        registry = ApplicationStatusRegistry(meterRegistry, "utv")
    }

    @Test
    fun `should register new metric`() {
        registry.add(data)
        val gauge = meterRegistry.get("application_status").tag("app_name", "reference").gauge()
        assertThat(gauge).isNotNull()
        assertThat(gauge.value()).isEqualTo(0.0)
        assertThat(registry.meterCache.size).isEqualTo(1)

        val infoGauge = meterRegistry.get("application_info").tag("app_name", "reference").gauge()
        assertThat(infoGauge).isNotNull()
    }

    @Test
    fun `should update metric with same tags, different health`() {
        registry.add(data)
        val gauge = meterRegistry.get("application_status").tag("app_name", "reference").gauge()
        assertThat(gauge).isNotNull()
        assertThat(gauge.value()).isEqualTo(0.0)
        assertThat(registry.meterCache.size).isEqualTo(1)

        val newData = data.copy(auroraStatus = AuroraStatus(AuroraStatusLevel.OBSERVE))
        registry.update(data, newData)

        val gauges = meterRegistry.get("application_status").tag("app_name", "reference").gauges()
        assertThat(gauges).isNotNull()
        assertThat(gauges.size).isEqualTo(1)
        assertThat(gauges.first().value()).isEqualTo(2.0)
        assertThat(registry.meterCache.size).isEqualTo(1)

        val infoGauges = meterRegistry.get("application_info").tag("app_name", "reference").gauges()
        assertThat(infoGauges).isNotNull()
        assertThat(infoGauges.size).isEqualTo(1)
    }

    @Test
    fun `should update metric with different tags`() {
        registry.add(data)
        val gauge = meterRegistry.get("application_status").tag("app_name", "reference").gauge()
        assertThat(gauge).isNotNull()
        assertThat(gauge.value()).isEqualTo(0.0)
        assertThat(registry.meterCache.size).isEqualTo(1)

        val infoGauges = meterRegistry.get("application_info").tag("app_name", "reference").gauges()
        assertThat(infoGauges).isNotNull()
        assertThat(infoGauges.size).isEqualTo(1)
        assertThat(infoGauges.first().id.getTag("app_version_strategy")).isEqualTo("1")

        val newData = data.copy(deployTag = "1.1")
        registry.update(data, newData)

        val gauges = meterRegistry.get("application_status").tag("app_name", "reference").gauges()
        assertThat(gauges).isNotNull()
        assertThat(gauges.size).isEqualTo(1)
        assertThat(gauges.first().value()).isEqualTo(0.0)
        assertThat(registry.meterCache.size).isEqualTo(1)

        val infoGaugesAfter = meterRegistry.get("application_info").tag("app_name", "reference").gauges()
        assertThat(infoGaugesAfter).isNotNull()
        assertThat(infoGaugesAfter.size).isEqualTo(1)
        assertThat(infoGaugesAfter.first().id.getTag("app_version_strategy")).isEqualTo("1.1")
    }

    @Test
    fun `should remove metric`() {
        registry.add(data)
        val gauge = meterRegistry.get("application_status").tag("app_name", "reference").gauge()
        assertThat(gauge).isNotNull()
        assertThat(gauge.value()).isEqualTo(0.0)
        assertThat(registry.meterCache.size).isEqualTo(1)

        registry.remove(data)
        assertThat {
            meterRegistry.get("application_status").tag("app_name", "reference").gauges()
        }.isFailure().all {
            isInstanceOf(MeterNotFoundException::class)
        }

        assertThat {
            meterRegistry.get("application_info").tag("app_name", "reference").gauges()
        }.isFailure().all {
            isInstanceOf(MeterNotFoundException::class)
        }
    }
}
