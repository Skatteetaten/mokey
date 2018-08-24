package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AuroraStatusCalculatorTest {

    @ParameterizedTest(name = "lastDeployment:{0} availableReplicas:{1} targetReplicas:{2} -> {3} {4}")
    @CsvSource(
        "Failed, 0, 0, DOWN, DEPLOY_FAILED_NO_PODS",
        "Failed, 2, 0, OBSERVE, DEPLOY_FAILED",
        "Complete, 0, 1, DOWN, 'NO_AVAILABLE_PODS'",
        "Complete, 1, 2, OBSERVE, TOO_MANY_PODS",
        "Complete, 0, 0, OFF, OFF",
        "Complete, 2, 1, OBSERVE, TOO_FEW_PODS",
        "Complete, 2, 2, HEALTHY, ''"
    )
    fun `Calculate status`(
        lastDeployment: String,
        availableReplicas: Int,
        targetReplicas: Int,
        expectedLevel: String,
        expectedComment: String
    ) {
        val deployDetails = DeployDetails(lastDeployment, availableReplicas, targetReplicas)
        val auroraStatus = AuroraStatus(deployDetails, emptyList())
        assert(auroraStatus.level).isEqualTo(AuroraStatusLevel.valueOf(expectedLevel))
        assert(auroraStatus.comment).isEqualTo(expectedComment)
    }
}
