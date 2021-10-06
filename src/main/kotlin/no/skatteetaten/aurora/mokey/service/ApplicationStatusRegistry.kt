package no.skatteetaten.aurora.mokey.service

import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.Meter.Id
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Tags
import mu.KotlinLogging
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
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

    val meterCache = ConcurrentHashMap<Id, AtomicInteger>()

    /*
     * Micrometer relies on the value if the gauge function to hold an AtomicInteger
     * That you can set to update the value.
     *
     * Since ApplicationData is immutable we have a separate meterCache that holds Meter.Id -> Value outside of the
     * meter registry.
     */
    fun update(old: ApplicationPublicData, data: ApplicationPublicData) {

        val oldMeter = createMeterId(old)
        val newMeter = createMeterId(data)

        if (oldMeter == newMeter) {
            meterCache[oldMeter]?.set(data.auroraStatus.level.level)
        } else {
            meterCache.remove(oldMeter)
            meterRegistry.remove(oldMeter)
            addToRegistry(newMeter, data.auroraStatus)
        }

        val oldInfoMeter = createInfoMeterId(old)
        val newInfoMeter = createInfoMeterId(data)

        if (oldInfoMeter != newInfoMeter) {
            meterRegistry.remove(oldInfoMeter)
            meterRegistry.gauge(newInfoMeter.name, newInfoMeter.tags, AtomicInteger(0))
        }
    }

    fun add(data: ApplicationPublicData) {
        addToRegistry(createMeterId(data), data.auroraStatus)

        val infoMeter = createInfoMeterId(data)
        meterRegistry.gauge(infoMeter.name, infoMeter.tags, AtomicInteger(0))
    }

    fun remove(data: ApplicationPublicData) {
        val meterId = createMeterId(data)
        val infoMeterId = createInfoMeterId(data)
        logger.info("Application is gone deleting meter={}", meterId)
        meterRegistry.remove(meterId)
        meterCache.remove(meterId)
        meterRegistry.remove(infoMeterId)
        meterCache.remove(meterId)
    }

    private fun addToRegistry(meterId: Id, status: AuroraStatus) {
        meterRegistry.gauge(meterId.name, meterId.tags, AtomicInteger(status.level.level))
            ?.let {
                meterCache[meterId] = it
            }
    }

    private fun createMetricsTags(data: ApplicationPublicData): List<Tag> {
        return listOf(
            Tag.of("app_namespace", data.namespace),
            Tag.of("app_environment", data.environment),
            Tag.of("app_cluster", openshiftCluster),
            Tag.of("app_name", data.applicationDeploymentName),
            Tag.of("app_source", openshiftCluster),
            Tag.of("app_type", "aurora-plattform"),
            Tag.of("app_businessgroup", data.affiliation ?: "")
        )
    }

    private fun createMeterId(data: ApplicationPublicData) =
        Id(
            "application_status",
            Tags.of(createMetricsTags(data)),
            null,
            "Status metric for applications. 0=OK, 1=OFF, 2=OBSERVE, 3=DOWN",
            Meter.Type.GAUGE
        )

    private fun createInfoMeterId(data: ApplicationPublicData) =
        Id(
            "application_info",
            Tags.of(listOf(
                    Tag.of("app_id", data.applicationDeploymentId ?: ""),
                    Tag.of("app_application_id", data.applicationId ?: ""),
                    Tag.of("app_version", data.auroraVersion ?: ""),
                    Tag.of("app_namespace", data.namespace),
                    Tag.of("app_environment", data.environment),
                    Tag.of("app_cluster", openshiftCluster),
                    Tag.of("app_name", data.applicationDeploymentName),
                    Tag.of("app_source", openshiftCluster),
                    Tag.of("app_type", "aurora-plattform"),
                    Tag.of("app_businessgroup", data.affiliation ?: ""),
                    Tag.of("app_version_strategy", data.deployTag),
                    Tag.of("app_message", data.message ?: ""),
                    Tag.of("app_release_to", data.releaseTo ?: "")
            )),
            null,
            "Info tag for application",
            Meter.Type.GAUGE
        )
}
