package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.StorageGridObjectAreaDetails
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class StorageGridObjectAreaService(
    val userClient: OpenShiftUserClient,
    @Value("\${openshift.cluster}") val cluster: String,
) {

    suspend fun findAllStorageGridObjectAreasForAffiliation(affiliation: String): List<StorageGridObjectAreaDetails> {
        return userClient.getProjectsInAffiliation(affiliation)
            .flatMap { userClient.getStorageGridObjectAreas(it.metadata.name) }
            .map {
                val tenant = "$affiliation-$cluster"
                StorageGridObjectAreaDetails(
                    name = it.metadata.name,
                    namespace = it.metadata.namespace,
                    creationTimestamp = it.metadata.creationTimestamp,
                    bucketPostfix = it.spec.bucketPostfix,
                    objectArea = it.spec.objectArea,
                    tenant = tenant,
                    bucketName = "$tenant-${it.spec.bucketPostfix}",
                    message = it.status.result.message,
                    reason = it.status.result.reason,
                    success = it.status.result.success
                )
            }
    }
}
