package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.PodDetails

/**
 * This file contains all methods that produce parameters that should be available for expansion in urls from
 * the Info endpoint for the applications.
 *
 * Example:
 * * {metricsHostname}/dashboard/db/openshift-project-spring-actuator-view-instance?var-ds=openshift-{cluster}-ose&var-namespace={namespace}&var-app={name}&var-instance={podName}</li>
 * * {WebSealAddress}/admin/</li>
 */

/**
 * Global parameters. These parameters are available for expansion for all urls.
 *
 * * metricsHostname: The url of the metrics host, including protocol. Example: http://metrics.skead.no
 * * cluster: The name of the current cluster. Example: utv
 */
val LinkBuilderFactory.expandParams
    get(): Map<String, String> = mapOf(
        "metricsHostname" to metricsHostname,
        "metricsHostName" to metricsHostname,
        "metricshostname" to metricsHostname,
        "splunkHostname" to splunkHostname,
        "splunkHostName" to splunkHostname,
        "splunkhostname" to splunkHostname,
        "cluster" to cluster
    )

/**
 * Application scoped parameters. These parameters are available for expansion for all urls.
 *
 * It should be possible to reference the routes/urls of the application when constructing application/service links in
 * the Info endpoint. For instance, if the application has a WebSealAddress, it should be possible to reference this
 * url when constructing another; {WebSealAddress}/admin/. This means that based on what urls are available for an
 * application, the following parameters are available for expansion:
 *
 * * namespace: The name of the OpenShift namespace the current application is deployed in
 * * name: The OpenShift name of the current application
 * * ServiceAddress:
 * * RouteAddress:
 * * WebSealAddress:
 * * BigIPAddress:
 */
val ApplicationData.expandParams
    get(): Map<String, String> {
        val addressParams = addresses.map { it.url.toString() to it::class.simpleName!! }.toMap()
        val metadataParams = mapOf("affiliation" to (affiliation ?: ""), "namespace" to namespace, "name" to applicationDeploymentName)
        val splunkParams = splunkIndex?.let {
            mapOf("splunkIndex" to it, "splunkindex" to it)
        } ?: mapOf()
        return addressParams + metadataParams + splunkParams
    }

/**
 * Pod scoped parameters. These parameters are available for expansion for Pod unique urls.
 *
 * * podName: The name of the current pod
 */
val PodDetails.expandParams get() = mapOf("podName" to openShiftPodExcerpt.name)
