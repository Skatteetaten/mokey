package no.skatteetaten.aurora.mokey.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import no.skatteetaten.aurora.kubernetes.crd.SkatteetatenCRD

fun newStorageGridObjectArea(block: StorageGridObjectArea.() -> Unit = {}): StorageGridObjectArea {
    val instance = StorageGridObjectArea()
    instance.block()

    return instance
}

@JsonIgnoreProperties
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(value = ["apiVersion", "kind", "metadata", "spec"])
@JsonDeserialize(using = JsonDeserializer.None::class)
class StorageGridObjectArea(
    var spec: StorageGridObjectAreaSpec = StorageGridObjectAreaSpec(),
    var status: StorageGridObjectAreaStatus = StorageGridObjectAreaStatus()
) : SkatteetatenCRD("storagegridobjectarea")

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StorageGridObjectAreaSpec(
    val applicationDeploymentId: String = "",
    val bucketPostfix: String = "",
    val objectArea: String = "",
    val tryReuseCredentials: Boolean = false,
)

data class StorageGridObjectAreaStatus(
    val result: StorageGridObjectAreaStatusResult = StorageGridObjectAreaStatusResult(),
    val retryCount: Int = 0
)

data class StorageGridObjectAreaStatusResult(
    val message: String = "",
    val reason: String = "",
    val success: Boolean = false
)
