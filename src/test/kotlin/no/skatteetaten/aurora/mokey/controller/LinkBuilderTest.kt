package no.skatteetaten.aurora.mokey.controller

import assertk.assert
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.springframework.hateoas.Link

class LinkBuilderTest {

    @Test
    fun `should expand placeholders correctly`() {
        val linkBuilder = LinkBuilder("", mapOf("metricsHostname" to "http://metrics.skead.no", "cluster" to "utv"))
        val link: Link = linkBuilder.createLink(
            "{metricsHostname}/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}&var-instance={podName}",
            "ServiceMetrics",
            mapOf("namespace" to "aurora", "name" to "mokey", "podName" to "mokey-1-acbea")
        )
        assert(link.href).isEqualTo("http://metrics.skead.no/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-utv-ose&var-namespace=aurora&var-app=mokey&var-instance=mokey-1-acbea")
    }

    @Test
    fun `should expand splunk placeholders correctly`() {
        val linkBuilder = LinkBuilder("", mapOf("splunkHostname" to "https://splunk.skead.no", "cluster" to "utv"))
        val link: Link = linkBuilder.createLink(
            "{splunkHostname}/en-GB/app/search/search?q=search%20index%3{splunkIndex}%20application%3D{name}",
            "ServiceMetrics",
            mapOf(
                "namespace" to "aurora",
                "name" to "mokey",
                "podName" to "mokey-1-acbea",
                "splunkIndex" to "openshift-test"
            )
        )
        assert(link.href).isEqualTo("https://splunk.skead.no/en-GB/app/search/search?q=search%20index%3openshift-test%20application%3Dmokey")
    }
}