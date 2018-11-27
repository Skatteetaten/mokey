package no.skatteetaten.aurora.mokey.service

import assertk.assert
import assertk.assertions.isEqualTo
import no.skatteetaten.aurora.mokey.PodDetailsDataBuilder
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.DOWN
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.HEALTHY
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OBSERVE
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel.OFF
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.HealthStatusDetail
import no.skatteetaten.aurora.mokey.model.OpenShiftContainerExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.Duration
import java.time.Instant

class AuroraStatusCalculatorTest {

    val calculator = AuroraStatusCalculator(
        avergageRestartObserveThreshold = 20,
        avergageRestartErrorThreshold = 100,
        differentDeploymentHourThreshold = 2
    )

    val time = Instant.EPOCH

    @ParameterizedTest(name = "test:{0}")
    @MethodSource("calculatorProvider")
    fun `Calculate status`(input: StatusCalculatorTestData) {
        input.apply {
            val deployDetails = DeployDetails(
                availableReplicas = input.availableReplicas,
                targetReplicas = input.targetReplicas,
                phase = input.lastDeployment,
                deployTag = "1",
                containers = mapOf("name" to "docker-registry/group/name@sha256:123456hash")
            )
            val auroraStatus = calculator.calculateStatus(deployDetails, pods, time)
            assert(auroraStatus).isEqualTo(expected)
        }
    }

    companion object {
        fun auroraStatus(main: HealthStatusDetail, vararg extra: HealthStatusDetail) =
            AuroraStatus(main.level, main.comment, statuses = extra.toSet() + main)

        fun auroraStatus(main: Pair<AuroraStatusLevel, String>, vararg extra: Pair<AuroraStatusLevel, String>) =
            AuroraStatus(main.first, main.second, statuses = extra.map {
                HealthStatusDetail(
                    it.first,
                    it.second
                )
            }.toSet() + HealthStatusDetail(main.first, main.second))

        @JvmStatic
        fun calculatorProvider(): List<StatusCalculatorTestData> {
            return listOf(
                StatusCalculatorTestData(
                    lastDeployment = "Failed",
                    availableReplicas = 0,
                    targetReplicas = 0,
                    expected = auroraStatus(
                        DOWN to "DEPLOY_FAILED_NO_PODS",
                        OFF to "OFF"
                    )
                ),
                StatusCalculatorTestData(
                    lastDeployment = "Failed",
                    availableReplicas = 1,
                    targetReplicas = 1,
                    expected = auroraStatus(OBSERVE to "DEPLOY_FAILED")
                ),
                StatusCalculatorTestData(
                    availableReplicas = 0,
                    targetReplicas = 1,
                    expected = auroraStatus(DOWN to "NO_AVAILABLE_PODS")
                ),
                StatusCalculatorTestData(
                    availableReplicas = 1,
                    expected = auroraStatus(OBSERVE to "TOO_FEW_PODS")
                ),
                StatusCalculatorTestData(
                    availableReplicas = 0,
                    targetReplicas = 0,
                    expected = auroraStatus(OFF to "OFF")
                ),
                StatusCalculatorTestData(
                    targetReplicas = 1,
                    expected = auroraStatus(OBSERVE to "TOO_MANY_PODS")
                ),
                StatusCalculatorTestData(
                    expected = auroraStatus(HEALTHY to "")
                ),
                StatusCalculatorTestData(
                    lastDeployment = "Running",
                    availableReplicas = 1,
                    expected = auroraStatus(HEALTHY to "DEPLOYMENT_IN_PROGRESS", OBSERVE to "TOO_FEW_PODS")
                ),
                StatusCalculatorTestData(
                    lastDeployment = null,
                    expected = auroraStatus(OFF to "NO_DEPLOYMENT")
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder().build()
                    ),
                    expected = auroraStatus(HealthStatusDetail(HEALTHY, "POD_HEALTH_CHECK", "name"))
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder(name = "app1").build(),
                        PodDetailsDataBuilder(name = "app2").build()
                    ),
                    expected = auroraStatus(
                        HealthStatusDetail(HEALTHY, "POD_HEALTH_CHECK", "app1"),
                        HealthStatusDetail(HEALTHY, "POD_HEALTH_CHECK", "app2")
                    )
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder(deployment = "name-1", startTime = Instant.EPOCH).build(),
                        PodDetailsDataBuilder(
                            deployment = "name-2",
                            startTime = Instant.EPOCH - Duration.ofDays(2)
                        ).build()
                    ),
                    expected = auroraStatus(
                        HealthStatusDetail(DOWN, "DIFFERENT_DEPLOYMENTS"),
                        HealthStatusDetail(HEALTHY, "POD_HEALTH_CHECK", "nam" +
                            "e")
                    )
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder(
                            containers = listOf(
                                OpenShiftContainerExcerpt(
                                    name = "name-java",
                                    state = "running",
                                    image = "docker....",
                                    restartCount = 101,
                                    ready = true,
                                    latestImage = true
                                )
                            )
                        ).build()
                    ),
                    expected = auroraStatus(
                        HealthStatusDetail(DOWN, "AVERAGE_RESTART_ABOVE_THRESHOLD"),
                        HealthStatusDetail(HEALTHY, "POD_HEALTH_CHECK", "name")
                    )
                ),
                StatusCalculatorTestData(
                    pods = listOf(
                        PodDetailsDataBuilder(
                            containers = listOf(
                                OpenShiftContainerExcerpt(
                                    name = "name-java",
                                    state = "running",
                                    image = "docker....",
                                    restartCount = 31,
                                    ready = true,
                                    latestImage = true
                                )
                            )
                        ).build()
                    ),
                    expected = auroraStatus(
                        HealthStatusDetail(OBSERVE, "AVERAGE_RESTART_ABOVE_THRESHOLD"),
                        HealthStatusDetail(HEALTHY, "POD_HEALTH_CHECK", "name")
                    )
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
