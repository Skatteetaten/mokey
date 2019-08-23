package no.skatteetaten.aurora.openshift.webclient

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class OpenShiftApiGroupTest {

    @ParameterizedTest
    @CsvSource(
        "APPLICATIONDEPLOYMENT, /apis/skatteetaten.no/v1/applicationdeployments",
        "DEPLOYMENTCONFIG, /apis/apps.openshift.io/v1/deploymentconfigs",
        "ROUTE, /apis/route.openshift.io/v1/routes",
        "USER, /apis/user.openshift.io/v1/users/~",
        "PROJECT, /apis/project.openshift.io/v1/projects",
        "IMAGESTREAMTAG, /apis/image.openshift.io/v1/imagestreamtags"
    )
    fun `Create path for OpenShift api group`(apiGroup: String, expectedPath: String) {
        val path = OpenShiftApiGroup.valueOf(apiGroup).path()
        assertThat(path).isEqualTo(expectedPath)
    }

    @ParameterizedTest
    @CsvSource(
        "SERVICE, /api/v1/services",
        "POD, /api/v1/pods",
        "REPLICATIONCONTROLLER, /api/v1/replicationcontrollers",
        "IMAGESTREAMTAG, /api/v1/imagestreamtags",
        "SELFSUBJECTACCESSREVIEW, /apis/authorization.k8s.io/v1/selfsubjectaccessreviews"
    )
    internal fun `Create path for Kubernetes api group`(apiGroup: String, expectedPath: String) {
        val path = KubernetesApiGroup.valueOf(apiGroup).path()
        assertThat(path).isEqualTo(expectedPath)
    }

    @Test
    fun `Create path for OpenShift api group with namespace`() {
        val path = OpenShiftApiGroup.APPLICATIONDEPLOYMENT.path("aurora")
        assertThat(path).isEqualTo("/apis/skatteetaten.no/v1/namespaces/aurora/applicationdeployments")
    }

    @Test
    fun `Create path for OpenShift api group with namespace and name`() {
        val path = OpenShiftApiGroup.APPLICATIONDEPLOYMENT.path("aurora", "app")
        assertThat(path).isEqualTo("/apis/skatteetaten.no/v1/namespaces/aurora/applicationdeployments/app")
    }

    @Test
    fun `Create label selector query param from Map`() {
        val labels = mapOf("1" to "a", "2" to "b", "3" to "c")
        val queryParams = OpenShiftApiGroup.APPLICATIONDEPLOYMENT.labelSelector(labels)
        assertThat(queryParams).isEqualTo("1=a,2=b,3=c")
    }
}