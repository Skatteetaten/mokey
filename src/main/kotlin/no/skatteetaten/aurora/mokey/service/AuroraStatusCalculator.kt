package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.StatusCheck
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
import no.skatteetaten.aurora.mokey.model.StatusCheckResult
import no.skatteetaten.aurora.mokey.service.auroraStatus.AnyPodDownCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AnyPodObserveCheck
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.Instant.now

@Service
class AuroraStatusCalculator(val statusChecks: List<StatusCheck>) {
    fun calculateAuroraStatus(app: DeployDetails, pods: List<PodDetails>, time: Instant = now()): AuroraStatus {
        val hasManagementHealthData = pods.any {
            it.managementData.health != null && it.managementData.health.isSuccess
        }
        // Removes AnyPodObserveCheck and AnyPodDownCheck when health data is missing or has failed.
        val checks = statusChecks.filter {
            when (it) {
                is AnyPodObserveCheck -> hasManagementHealthData
                is AnyPodDownCheck -> hasManagementHealthData
                else -> true
            }
        }
        val results = checks.map {
            val result = it.isFailing(app, pods, time)
            StatusCheckResult(it, result)
        }
        val level = calculateAuroraStatusLevel(results)
        val reports = results.filterNot { it.statusCheck.isOverridingAuroraStatus }.map(this::toReport)
        val reasons = results.filter { it.hasFailed }.map(this::toReport)

        return AuroraStatus(level, reasons, reports)
    }

    private fun calculateAuroraStatusLevel(statusCheckResults: List<StatusCheckResult>): AuroraStatusLevel {
        if (statusCheckResults.none { it.hasFailed }) return HEALTHY

        statusCheckResults.filter { it.hasFailed }
            .firstOrNull { it.statusCheck.isOverridingAuroraStatus }
            ?.let {
                return it.statusCheck.failLevel
            }

        return statusCheckResults
            .filter { it.hasFailed }
            .reduce { acc: StatusCheckResult, result: StatusCheckResult ->
                if (result.statusCheck.failLevel.level > acc.statusCheck.failLevel.level) result else acc
            }.statusCheck.failLevel
    }

    private fun toReport(result: StatusCheckResult): StatusCheckReport = StatusCheckReport(
        name = result.statusCheck.name,
        description = result.description,
        failLevel = result.statusCheck.failLevel,
        hasFailed = result.hasFailed
    )
}
