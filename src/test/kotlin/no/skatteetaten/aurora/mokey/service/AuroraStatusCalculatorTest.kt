package no.skatteetaten.aurora.mokey.service

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.OpenShiftContainerExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.model.StatusCheck
import no.skatteetaten.aurora.mokey.model.StatusCheckReport
import no.skatteetaten.aurora.mokey.service.auroraStatus.AnyPodDownCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AnyPodObserveCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.ApplicationScaledDownCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AverageRestartErrorCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AverageRestartObserveCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeployFailedCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeploymentInProgressCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DifferentDeploymentCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.NoAvailablePodsCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.NoDeploymentCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.OffCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.TooFewPodsCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.TooManyPodsCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.time.Duration
import java.time.Instant

class AuroraStatusCalculatorTest {

    val calculator = AuroraStatusCalculator(
        listOf(
            anyPodDown,
            anyPodObserve,
            noAvailablePods,
            noDeployment,
            off,
            deployFailed,
            deploymentInProgress,
            tooManyPods,
            tooFewPods,
            averageRestartError,
            averageRestartObserve,
            differentDeployment,
            scaledDown
        )
    )
    val time = Instant.EPOCH

    @ParameterizedTest
    @EnumSource(StatusCalculatorTestData::class)
    fun `Calculate status`(input: StatusCalculatorTestData) {
        input.apply {
            val deployDetails = DeployDetails(
                availableReplicas = input.availableReplicas,
                targetReplicas = input.targetReplicas,
                phase = input.lastDeployment,
                deployTag = "1",
                scaledDown = input.scaledDown
            )
            val auroraStatus = calculator.calculateAuroraStatus(deployDetails, pods, time)
            assertThat(auroraStatus.level).isEqualTo(expected.level)
            assertThat(auroraStatus.reasons.sortedBy { it.name }).isEqualTo(expected.reasons.sortedBy { it.name })
        }
    }

    companion object {
        val averageRestartErrorThreshold = 100
        val averageRestartObserveThreshold = 20
        val hourThreshold = 20L

        val anyPodDown = AnyPodDownCheck()
        val anyPodObserve = AnyPodObserveCheck()
        val noAvailablePods = NoAvailablePodsCheck()
        val noDeployment = NoDeploymentCheck()
        val off = OffCheck()
        val deployFailed = DeployFailedCheck()
        val deploymentInProgress = DeploymentInProgressCheck()
        val tooManyPods = TooManyPodsCheck()
        val tooFewPods = TooFewPodsCheck()
        val averageRestartError = AverageRestartErrorCheck(averageRestartErrorThreshold)
        val averageRestartObserve = AverageRestartObserveCheck(averageRestartObserveThreshold)
        val differentDeployment = DifferentDeploymentCheck(hourThreshold)
        val scaledDown = ApplicationScaledDownCheck()
    }

    enum class StatusCalculatorTestData(
        val lastDeployment: String? = "Complete",
        val availableReplicas: Int = 2,
        val targetReplicas: Int = 2,
        val scaledDown: String? = null,
        val pods: List<PodDetails> = listOf(
            PodDetailsDataBuilder(deployment = "name-1", startTime = Instant.EPOCH).build(),
            PodDetailsDataBuilder(deployment = "name-2", startTime = Instant.EPOCH).build()
        ),
        val expected: AuroraStatus
    ) {

        LAST_DEPLOYMENT_FAILED_UNAVAILABLE(
            lastDeployment = "Failed",
            availableReplicas = 0,
            targetReplicas = 1,
            pods = listOf(),
            expected = AuroraStatus(DOWN, toReport(deployFailed, noAvailablePods, tooFewPods))
        ),
        LAST_DEPLOYMENT_FAILED(
            lastDeployment = "Failed",
            expected = AuroraStatus(OBSERVE, toReport(deployFailed))
        ),
        APPLICATION_DOWN(
            availableReplicas = 0,
            targetReplicas = 1,
            pods = listOf(),
            expected = AuroraStatus(DOWN, toReport(noAvailablePods, tooFewPods))
        ),

        TOO_FEW_PODS(
            availableReplicas = 1,
            pods = listOf(
                PodDetailsDataBuilder(deployment = "name-1", startTime = Instant.EPOCH).build()
            ),
            expected = AuroraStatus(
                OBSERVE, toReport(tooFewPods)
            )
        )

        ,
        APPLICATION_TURNED_OFF(
            availableReplicas = 0,
            targetReplicas = 0,
            pods = listOf(),
            expected = AuroraStatus(OFF, toReport(off))
        ),
        TOO_MANY_PODS(
            targetReplicas = 1,
            expected = AuroraStatus(OBSERVE, toReport(tooManyPods))
        ),
        APPLICATION_IS_HEALTHY(
            expected = AuroraStatus(HEALTHY)
        ),
        DEPLOYMENT_IN_PROGRESS(
            lastDeployment = "Running",
            availableReplicas = 1,
            pods = listOf(
                PodDetailsDataBuilder(deployment = "name-1", startTime = Instant.EPOCH).build()
            ),
            expected = AuroraStatus(HEALTHY, toReport(deploymentInProgress, tooFewPods))
        ),
        NO_DEPLOYMENT_MADE(
            lastDeployment = null,
            expected = AuroraStatus(OFF, toReport(noDeployment))
        ),
        PODS_IN_DIFFERENT_DEPLOYMENTS(
            pods = listOf(
                PodDetailsDataBuilder(deployment = "name-1", startTime = Instant.EPOCH).build(),
                PodDetailsDataBuilder(
                    deployment = "name-2",
                    startTime = Instant.EPOCH - Duration.ofDays(2)
                ).build()
            ),
            expected = AuroraStatus(DOWN, toReport(differentDeployment))
        ),
        POD_RESTARTED_OVER_DOWN_THRESHOLD(
            availableReplicas = 1,
            targetReplicas = 1,
            pods = listOf(
                PodDetailsDataBuilder(
                    containers = listOf(
                        OpenShiftContainerExcerpt(
                            name = "name-java",
                            state = "running",
                            image = "docker....",
                            restartCount = averageRestartErrorThreshold + 10,
                            ready = true
                        )
                    )
                ).build()
            ),
            expected = AuroraStatus(DOWN, toReport(averageRestartError, averageRestartObserve))
        ),
        POD_RESTARTED_OVER_OBSERVE_THRESHOLD(
            availableReplicas = 1,
            targetReplicas = 1,
            pods = listOf(
                PodDetailsDataBuilder(
                    containers = listOf(
                        OpenShiftContainerExcerpt(
                            name = "name-java",
                            state = "running",
                            image = "docker....",
                            restartCount = averageRestartObserveThreshold + 10,
                            ready = true
                        )
                    )
                ).build()
            ),
            expected = AuroraStatus(OBSERVE, toReport(averageRestartObserve))
        ),
        DESTRUCTOR_SCALED_DOWN(
            scaledDown = "CrashLoopBackOff",
            expected = AuroraStatus(OBSERVE, toReport(scaledDown))
        )

    }
}

fun toReport(vararg statusChecks: StatusCheck): List<StatusCheckReport> {
    return statusChecks.map {
        StatusCheckReport(
            name = it.name,
            description = it.description.failed,
            hasFailed = true,
            failLevel = it.failLevel
        )
    }
}