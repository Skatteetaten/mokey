package no.skatteetaten.aurora.mokey.service

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

@Service
class ApplicationStatusRegistry(
    val meterRegistry: MeterRegistry,
    @Value("\${openshift.cluster}") val openshiftCluster: String
) {

    val meterCache = ConcurrentHashMap<Meter.Id, AtomicInteger>()

    /*
     * Micrometer relies on the value if the gauge function to hold an AtomicInteger
     * That you can set to update the value.
     *
     * Since ApplicationData is immutable we have a separate meterCache that holds Meter.Id -> Value outside of the
     * meter registry.
     */
    fun update(old: ApplicationData, data: ApplicationData) {

        val oldMeter = createMeterId(old)
        val newMeter = createMeterId(data)

        if (oldMeter == newMeter) {
            meterCache[oldMeter]?.set(data.auroraStatus.level.level)
        } else {
            meterCache.remove(oldMeter)
            meterRegistry.remove(oldMeter)
            addToRegistry(newMeter, data.auroraStatus)
        }
    }

    fun add(data: ApplicationData) {
        val meterId = createMeterId(data)
        addToRegistry(meterId, data.auroraStatus)
    }

    fun remove(data: ApplicationData) {
        val meterId = createMeterId(data)
        logger.info("Application is gone deleting meter={}", meterId)
        meterRegistry.remove(meterId)
        meterCache.remove(meterId)
    }

    private fun addToRegistry(meterId: Meter.Id, status: AuroraStatus) {

        meterRegistry.gauge(meterId.name, meterId.tags, AtomicInteger(status.level.level))
            ?.let {
                meterCache[meterId] = it
            }
    }

    private fun createMetricsTags(data: ApplicationData): List<Tag> {
        return listOf(
            Tag.of("app_version", data.publicData.auroraVersion ?: ""),
            Tag.of("app_namespace", data.publicData.namespace),
            Tag.of("app_environment", data.publicData.environment),
            Tag.of("app_cluster", openshiftCluster),
            Tag.of("app_name", data.publicData.applicationDeploymentName),
            Tag.of("app_id", data.publicData.applicationDeploymentId),
            Tag.of("app_source", openshiftCluster),
            Tag.of("app_type", "aurora-plattform"),
            Tag.of("app_businessgroup", data.publicData.affiliation ?: ""),
            Tag.of("app_version_strategy", data.publicData.deployTag)
        )
    }

    private fun createMeterId(data: ApplicationData) =
        Meter.Id("application_status", Tags.of(createMetricsTags(data)), null, null, Meter.Type.GAUGE)
}
