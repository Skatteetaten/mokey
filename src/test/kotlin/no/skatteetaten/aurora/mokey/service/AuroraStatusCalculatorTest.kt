package no.skatteetaten.aurora.mokey.service

import assertk.assert
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
import no.skatteetaten.aurora.mokey.service.auroraStatus.AnyPodDownCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AnyPodObserveCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AverageRestartErrorCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.AverageRestartObserveCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeployFailedCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeployFailedNoPodsCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DeploymentInProgressCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.DifferentDeploymentCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.NoAvailablePodsCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.NoDeploymentCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.OffCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.TooFewPodsCheck
import no.skatteetaten.aurora.mokey.service.auroraStatus.TooManyPodsCheck
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
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
            deployFailedNoPods,
            deploymentInProgress,
            tooManyPods,
            tooFewPods,
            averageRestartError,
            averageRestartObserve,
            differentDeployment
        )
    )
    val time = Instant.EPOCH

    @ParameterizedTest(name = "test:{0}")
    @MethodSource("calculatorProvider")
    fun `Calculate status`(input: StatusCalculatorTestData) {
        println(calculator.deploymentChecks)
        input.apply {
            val deployDetails = DeployDetails(
                availableReplicas = input.availableReplicas,
                targetReplicas = input.targetReplicas,
                phase = input.lastDeployment,
                deployTag = "1"
            )
            val auroraStatus = calculator.calculateStatus(deployDetails, pods, time)
            assert(auroraStatus.statusCheckName).isEqualTo(expected.statusCheckName)
            assert(auroraStatus.level).isEqualTo(expected.level)
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
        val deployFailedNoPods = DeployFailedNoPodsCheck()
        val deploymentInProgress = DeploymentInProgressCheck()
        val tooManyPods = TooManyPodsCheck()
        val tooFewPods = TooFewPodsCheck()
        val averageRestartError = AverageRestartErrorCheck(averageRestartErrorThreshold)
        val averageRestartObserve = AverageRestartObserveCheck(averageRestartObserveThreshold)
        val differentDeployment = DifferentDeploymentCheck(hourThreshold)

        @JvmStatic
        fun calculatorProvider(): List<StatusCalculatorTestData> {
            return listOf(
                StatusCalculatorTestData(
                    lastDeployment = "Failed",
                    availableReplicas = 0,
                    targetReplicas = 0,
                    expected = AuroraStatus(DOWN, deployFailedNoPods.name)
                ),
                StatusCalculatorTestData(
                    lastDeployment = "Failed",
                    availableReplicas = 1,
                    targetReplicas = 1,
                    expected = AuroraStatus(OBSERVE, deployFailed.name)
                ),
                StatusCalculatorTestData(
                    availableReplicas = 0,
                    targetReplicas = 1,
                    expected = AuroraStatus(DOWN, noAvailablePods.name)
                ),
                StatusCalculatorTestData(
                    availableReplicas = 1,
                    expected = AuroraStatus(OBSERVE, tooFewPods.name)
                ),
                StatusCalculatorTestData(
                    availableReplicas = 0,
                    targetReplicas = 0,
                    expected = AuroraStatus(OFF, off.name)
                ),
                StatusCalculatorTestData(
                    targetReplicas = 1,
                    expected = AuroraStatus(OBSERVE, tooManyPods.name)
                ),
                StatusCalculatorTestData(
                    expected = AuroraStatus(HEALTHY, "")
                ),
                StatusCalculatorTestData(
                    lastDeployment = "Running",
                    availableReplicas = 1,
                    expected = AuroraStatus(HEALTHY, deploymentInProgress.name)
                ),
                StatusCalculatorTestData(
                    lastDeployment = null,
                    expected = AuroraStatus(OFF, noDeployment.name)
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder().build()
                    ),
                    expected = AuroraStatus(HEALTHY, "")
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder(deployment = "name-1", startTime = Instant.EPOCH).build(),
                        PodDetailsDataBuilder(
                            deployment = "name-2",
                            startTime = Instant.EPOCH - Duration.ofDays(2)
                        ).build()
                    ),
                    expected = AuroraStatus(DOWN, differentDeployment.name)
                ),
                StatusCalculatorTestData(
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
                    expected = AuroraStatus(DOWN, averageRestartError.name)
                ),
                StatusCalculatorTestData(
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
                    expected = AuroraStatus(OBSERVE, averageRestartObserve.name)
                )
            )
        }
    }

    data class StatusCalculatorTestData(
        val lastDeployment: String? = "Complete",
        val availableReplicas: Int = 2,
        val targetReplicas: Int = 2,
        val pods: List<PodDetails> = listOf(),
        val expected: AuroraStatus
    )
}
