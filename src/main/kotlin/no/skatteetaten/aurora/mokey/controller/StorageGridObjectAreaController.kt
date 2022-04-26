package no.skatteetaten.aurora.mokey.controller

import no.skatteetaten.aurora.mokey.model.StorageGridObjectAreaDetails
import no.skatteetaten.aurora.mokey.service.StorageGridObjectAreaService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(StorageGridObjectAreaController.path)
class StorageGridObjectAreaController(
    val storageGridObjectAreaService: StorageGridObjectAreaService
) {
    @GetMapping
    suspend fun getAll(@RequestParam(name = "affiliation") affiliation: String): List<StorageGridObjectAreaDetails> {
        return storageGridObjectAreaService.findAllStorageGridObjectAreasForAffiliation(affiliation)
            .map {
                StorageGridObjectAreaDetails(
                    name = it.metadata.name,
                    namespace = it.metadata.namespace,
                    creationTimestamp = it.metadata.creationTimestamp,
                    bucketPostfix = it.spec.bucketPostfix,
                    objectArea = it.spec.objectArea,
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
