package no.skatteetaten.aurora.mokey.controller

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test

class LinkBuilderTest {

    @Test
    fun `should expand placeholders correctly`() {
        val linkBuilder = LinkBuilder("", mapOf("metricsHostname" to "http://metrics.skead.no", "cluster" to "utv"))
        val link = linkBuilder.createLink(
            "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}&var-instance={podName}",
            "ServiceMetrics",
            mapOf("namespace" to "aurora", "name" to "mokey", "podName" to "mokey-1-acbea")
        )
        assertThat(link.second).isEqualTo("http://metrics.skead.no/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-utv-ose&var-namespace=aurora&var-app=mokey&var-instance=mokey-1-acbea")
    }

    @Test
    fun `should expand splunk placeholders correctly`() {
        val linkBuilder = LinkBuilder("", mapOf("splunkHostname" to "https://splunk.skead.no", "cluster" to "utv"))
        val link = linkBuilder.createLink(
            "{splunkHostname}/en-GB/app/search/search?q=search%20index%3{splunkIndex}%20application%3D{name}",
            "ServiceMetrics",
            mapOf(
                "namespace" to "aurora",
                "name" to "mokey",
                "podName" to "mokey-1-acbea",
                "splunkIndex" to "openshift-test"
            )
        )
        assertThat(link.second).isEqualTo("https://splunk.skead.no/en-GB/app/search/search?q=search%20index%3openshift-test%20application%3Dmokey")
    }

//    @Test
//    fun `should create files links`() {
//
//        val linkBuilder = LinkBuilder("https://boober", mapOf())
//
//        val (current, deployed) = linkBuilder.files(
//            ApplicationDeploymentCommand(
//                applicationDeploymentRef = ApplicationDeploymentRef(
//                    environment = "foo",
//                    application = "bar"
//                ),
//                auroraConfig = AuroraConfigRef(
//                    name = "jedi",
//                    refName = "master",
//                    resolvedRef = "123"
//                )
//            )
//        )
//
//        assertThat(current.href).isEqualTo("https://boober/v1/auroraconfig/jedi?environment=foo&application=bar&reference=master")
//        assertThat(deployed.href).isEqualTo("https://boober/v1/auroraconfig/jedi?environment=foo&application=bar&reference=123")
//    }
//
//    @Test
//    fun `should create deploymentSepc links`() {
//
//        val linkBuilder = LinkBuilder("https://boober", mapOf())
//
//        val (current, deployed) = linkBuilder.deploymentSpec(
//            ApplicationDeploymentCommand(
//                applicationDeploymentRef = ApplicationDeploymentRef(
//                    environment = "foo",
//                    application = "bar"
//                ),
//                auroraConfig = AuroraConfigRef(
//                    name = "jedi",
//                    refName = "master",
//                    resolvedRef = "123"
//                )
//            )
//        )
//
//        assertThat(current.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?reference=master")
//        assertThat(deployed.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?reference=123")
//    }
//
//    @Test
//    fun `should create deploymentSepc links with overrides`() {
//
//        val linkBuilder = LinkBuilder("https://boober", mapOf())
//
//        val (current, deployed) = linkBuilder.deploymentSpec(
//            ApplicationDeploymentCommand(
//                overrideFiles = mapOf("foo/bar.json" to "version=1"),
//                applicationDeploymentRef = ApplicationDeploymentRef(
//                    environment = "foo",
//                    application = "bar"
//                ),
//                auroraConfig = AuroraConfigRef(
//                    name = "jedi",
//                    refName = "master",
//                    resolvedRef = "123"
//                )
//            )
//        )
//
//        val overrideValue = "%7B%22foo/bar.json%22:%22version%3D1%22%7D"
//        assertThat(current.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?overrides=$overrideValue&reference=master")
//        assertThat(deployed.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?overrides=$overrideValue&reference=123")
//
//        val files: Map<String, String> = jacksonObjectMapper()
//            .readValue(UriUtils.decode(overrideValue, Charset.defaultCharset().toString()))
//        assertThat(files["foo/bar.json"] == "version=1")
//    }
//
//    @Test
//    fun `should create OpenShift Console links`() {
//        val linkBuilder = LinkBuilder("", mapOf("cluster" to "utv"))
//
//        val links = linkBuilder.openShiftConsoleLinks("noodlenose-8981", "paas-test")
//
//        assertThat(links.find { it.rel == "ocp_console_details" }).isNotNull()
//        assertThat(links.find { it.rel == "ocp_console_environment" }).isNotNull()
//        assertThat(links.find { it.rel == "ocp_console_terminal" }).isNotNull()
//        assertThat(links.find { it.rel == "ocp_console_events" }).isNotNull()
//        assertThat(links.find { it.rel == "ocp_console_log" }).isNotNull()
//    }
}