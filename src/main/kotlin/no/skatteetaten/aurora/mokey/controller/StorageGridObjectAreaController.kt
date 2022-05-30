package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.StorageGridObjectAreaDetails
import no.skatteetaten.aurora.mokey.service.StorageGridObjectAreaService
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(StorageGridObjectAreaController.path)
class StorageGridObjectAreaController(
    val storageGridObjectAreaService: StorageGridObjectAreaService,
    @Value("\${openshift.cluster}") val cluster: String,
) {
    @GetMapping
    suspend fun getAll(@RequestParam affiliation: String): List<StorageGridObjectAreaDetails> {
        return storageGridObjectAreaService.getStorageGridObjectAreasForAffiliationFromCache(affiliation)
            .map {
                val tenant = "$affiliation-$cluster"
                StorageGridObjectAreaDetails(
                    applicationDeploymentId = it.spec.applicationDeploymentId,
                    name = it.metadata.name,
                    namespace = it.metadata.namespace,
                    creationTimestamp = it.metadata.creationTimestamp,
                    objectArea = it.spec.objectArea,
                    tenant = tenant,
                    bucketName = "$tenant-${it.spec.bucketPostfix}",
                    message = it.status.result.message,
                    reason = it.status.result.reason,
                    success = it.status.result.success
                )
            }
    }

    companion object {
        const val path = "/api/auth/storagegridobjectarea"
    }
}
