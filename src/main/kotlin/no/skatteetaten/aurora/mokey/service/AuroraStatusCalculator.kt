package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.StatusCheck
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
import no.skatteetaten.aurora.mokey.model.StatusCheckResult
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeploymentInProgressCheck
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.Instant.now

@Service
class AuroraStatusCalculator(val deploymentChecks: List<StatusCheck>) {

    fun calculateStatus(app: DeployDetails, pods: List<PodDetails>, time: Instant = now()): AuroraStatus {

        val results = (deploymentChecks).map {
            val result = it.isFailing(app, pods, time)
            StatusCheckResult(it, result)
        }

        val reports = results.map {
            StatusCheckReport(
                name = it.statusCheck.name,
                description = it.statusCheck.description,
                failLevel = it.statusCheck.failLevel,
                hasFailed = it.hasFailed
            )
        }

        val checkResult = calculateStatus(results)

        return if (checkResult == null) {
            AuroraStatus(AuroraStatusLevel.HEALTHY, "", "", reports)
        } else {
            checkResult.let {
                AuroraStatus(it.statusCheck.failLevel, it.statusCheck.name, it.statusCheck.description, reports)
            }
        }
    }

    private fun calculateStatus(statusCheckResults: List<StatusCheckResult>): StatusCheckResult? {

        if (statusCheckResults.none { it.hasFailed }) {
            return null
        }

        statusCheckResults.find { it.statusCheck is DeploymentInProgressCheck && it.hasFailed }
            ?.let { return it }

        return statusCheckResults
            .filter { it.hasFailed }
            .reduce { acc: StatusCheckResult, result: StatusCheckResult ->
                if (result.statusCheck.failLevel.level > acc.statusCheck.failLevel.level) result else acc
            }.let { it }
    }
}