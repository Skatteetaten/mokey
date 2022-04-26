package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.StorageGridObjectArea
import org.springframework.stereotype.Service

@Service
class StorageGridObjectAreaService(
    val userClient: OpenShiftUserClient
) {

    suspend fun findAllStorageGridObjectAreasForAffiliation(affiliation: String): List<StorageGridObjectArea> {
        return userClient.getProjectsInAffiliation(affiliation)
            .map { userClient.getStorageGridObjectAreas(it.metadata.name) }
            .flatten()
    }
}
