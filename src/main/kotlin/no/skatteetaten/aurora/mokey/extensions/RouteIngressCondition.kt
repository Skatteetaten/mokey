package no.skatteetaten.aurora.mokey.extensions

import io.fabric8.openshift.api.model.RouteIngressCondition

fun RouteIngressCondition.success(): Boolean = this.type == "Admitted" && this.status == "True"
