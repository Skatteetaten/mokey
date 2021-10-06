package no.skatteetaten.aurora.mokey.controller

import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import org.junit.jupiter.api.Test
import org.springframework.web.util.UriUtils
import java.nio.charset.Charset

class LinkBuilderTest {

    @Test
    fun `should expand placeholders correctly`() {
        val linkBuilder = LinkBuilder("", mapOf("metricsHostname" to "http://metrics.skead.no", "cluster" to "utv"))
        val link = linkBuilder.createLink(
            "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}&var-instance={podName}",
            "ServiceMetrics",
            mapOf("namespace" to "aurora", "name" to "mokey", "podName" to "mokey-1-acbea")
        )
        assertThat(link.href).isEqualTo("http://metrics.skead.no/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-utv-ose&var-namespace=aurora&var-app=mokey&var-instance=mokey-1-acbea")
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
        assertThat(link.href).isEqualTo("https://splunk.skead.no/en-GB/app/search/search?q=search%20index%3openshift-test%20application%3Dmokey")
    }

    @Test
    fun `should create files links`() {

        val linkBuilder = LinkBuilder("https://boober", mapOf())

        val (current, deployed) = linkBuilder.files(
            ApplicationDeploymentCommand(
                applicationDeploymentRef = ApplicationDeploymentRef(
                    environment = "foo",
                    application = "bar"
                ),
                auroraConfig = AuroraConfigRef(
                    name = "jedi",
                    refName = "master",
                    resolvedRef = "123"
                )
            )
        )

        assertThat(current.href).isEqualTo("https://boober/v1/auroraconfig/jedi?environment=foo&application=bar&reference=master")
        assertThat(deployed.href).isEqualTo("https://boober/v1/auroraconfig/jedi?environment=foo&application=bar&reference=123")
    }

    @Test
    fun `should create deploymentSpec links`() {

        val linkBuilder = LinkBuilder("https://boober", mapOf())

        val (current, deployed) = linkBuilder.deploymentSpec(
            ApplicationDeploymentCommand(
                applicationDeploymentRef = ApplicationDeploymentRef(
                    environment = "foo",
                    application = "bar"
                ),
                auroraConfig = AuroraConfigRef(
                    name = "jedi",
                    refName = "master",
                    resolvedRef = "123"
                )
            )
        )

        assertThat(current.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?reference=master")
        assertThat(deployed.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?reference=123")
    }

    @Test
    fun `should create deploymentSpec links with overrides`() {

        val linkBuilder = LinkBuilder("https://boober", mapOf())

        val (current, deployed) = linkBuilder.deploymentSpec(
            ApplicationDeploymentCommand(
                overrideFiles = mapOf("foo/bar.json" to "version=1"),
                applicationDeploymentRef = ApplicationDeploymentRef(
                    environment = "foo",
                    application = "bar"
                ),
                auroraConfig = AuroraConfigRef(
                    name = "jedi",
                    refName = "master",
                    resolvedRef = "123"
                )
            )
        )

        val overrideValue = "%7B%22foo/bar.json%22:%22version%3D1%22%7D"
        assertThat(current.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?overrides=$overrideValue&reference=master")
        assertThat(deployed.href).isEqualTo("https://boober/v1/auroradeployspec/jedi/foo/bar?overrides=$overrideValue&reference=123")

        val files: Map<String, String> = jacksonObjectMapper()
            .readValue(UriUtils.decode(overrideValue, Charset.defaultCharset().toString()))
        assertThat(files["foo/bar.json"] == "version=1")
    }

    @Test
    fun `should create OpenShift Console links`() {
        val linkBuilder = LinkBuilder("", mapOf("cluster" to "utv"))

        val links = linkBuilder.openShiftConsoleLinks("noodlenose-8981", "paas-test")

        val baseUrl = "https://utv-master.paas.skead.no:8443/console/project/paas-test/browse/pods/noodlenose-8981?tab"
        assertThat(links.find { it.rel == "ocp_console_details" }?.href).isEqualTo("$baseUrl=details")
        assertThat(links.find { it.rel == "ocp_console_environment" }?.href).isEqualTo("$baseUrl=environment")
        assertThat(links.find { it.rel == "ocp_console_terminal" }?.href).isEqualTo("$baseUrl=terminal")
        assertThat(links.find { it.rel == "ocp_console_events" }?.href).isEqualTo("$baseUrl=events")
        assertThat(links.find { it.rel == "ocp_console_logs" }?.href).isEqualTo("$baseUrl=logs")
    }

    @Test
    fun `should create OpenShift Console links openshift4`() {
        val linkBuilder = LinkBuilder("", mapOf("cluster" to "utv"), 4)

        val links = linkBuilder.openShiftConsoleLinks("noodlenose-8981", "paas-test")

        val baseUrl = "https://console-openshift-console.apps.utv.paas.skead.no/k8s/ns/paas-test/pods/noodlenose-8981"
        assertThat(links.find { it.rel == "ocp_console_details" }?.href).isEqualTo(baseUrl)
        assertThat(links.find { it.rel == "ocp_console_environment" }?.href).isEqualTo("$baseUrl/environment")
        assertThat(links.find { it.rel == "ocp_console_terminal" }?.href).isEqualTo("$baseUrl/terminal")
        assertThat(links.find { it.rel == "ocp_console_events" }?.href).isEqualTo("$baseUrl/events")
        assertThat(links.find { it.rel == "ocp_console_logs" }?.href).isEqualTo("$baseUrl/logs")
    }
}
