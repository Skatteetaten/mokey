package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.StatusCheck
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
import no.skatteetaten.aurora.mokey.model.StatusCheckResult
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeploymentInProgressCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.NoDeploymentCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.OffCheck
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.Instant.now

@Service
class AuroraStatusCalculator(val deploymentChecks: List<StatusCheck>) {

    fun calculateAuroraStatus(app: DeployDetails, pods: List<PodDetails>, time: Instant = now()): AuroraStatus {

        val results = (deploymentChecks).map {
            val result = it.isFailing(app, pods, time)
            StatusCheckResult(it, result)
        }

        val level = calculateAuroraStatusLevel(results)
        val reports = results.filterNot(this::isSpecialCheck).map(this::toReport)
        val reasons = results.filter { it.hasFailed }.map(this::toReport)


        return AuroraStatus(level, reasons, reports)
    }

    private fun calculateAuroraStatusLevel(statusCheckResults: List<StatusCheckResult>): AuroraStatusLevel {

        if (statusCheckResults.none { it.hasFailed }) {
            return HEALTHY
        }

        statusCheckResults.filter { it.hasFailed }.firstOrNull(this::isSpecialCheck)?.let {
            return it.statusCheck.failLevel
        }

        return statusCheckResults
            .filter { it.hasFailed }
            .reduce { acc: StatusCheckResult, result: StatusCheckResult ->
                if (result.statusCheck.failLevel.level > acc.statusCheck.failLevel.level) result else acc
            }.let { it.statusCheck.failLevel }
    }

    private fun isSpecialCheck(result: StatusCheckResult) = when (result.statusCheck) {
        is DeploymentInProgressCheck -> true
        is OffCheck -> true
        is NoDeploymentCheck -> true
        else -> false
    }

    private fun toReport(result: StatusCheckResult): StatusCheckReport {
        return StatusCheckReport(
            name = result.statusCheck.name,
            description = result.statusCheck.description,
            failLevel = result.statusCheck.failLevel,
            hasFailed = result.hasFailed
        )
    }
}