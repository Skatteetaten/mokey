package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SelfSubjectAccessReview(
    val kind: String = "SelfSubjectAccessReview",
    val apiVersion: String = "authorization.k8s.io/v1",
    val spec: SelfSubjectAccessReviewSpec,
    val status: SelfSubjectAccessReviewStatus = SelfSubjectAccessReviewStatus()
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SelfSubjectAccessReviewSpec(
    val resourceAttributes: SelfSubjectAccessReviewResourceAttributes
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SelfSubjectAccessReviewResourceAttributes(
    val namespace: String,
    val verb: String,
    val resource: String
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SelfSubjectAccessReviewStatus(
    val allowed: Boolean = false
)
