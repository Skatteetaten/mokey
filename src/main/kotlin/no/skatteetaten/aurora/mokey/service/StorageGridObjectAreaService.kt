package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.extensions.affiliation
import no.skatteetaten.aurora.mokey.model.StorageGridObjectArea
import org.springframework.stereotype.Service

@Service
class StorageGridObjectAreaService(
    val serviceAccountClient: OpenShiftServiceAccountClient,
    val userClient: OpenShiftUserClient
) {

    suspend fun findAllStorageGridObjectAreasForAffiliation(affiliation: String): List<StorageGridObjectArea> {
        val projectNames = userClient.getAllProjects()
            .filter { it.metadata.affiliation == affiliation }
            .map { it.metadata.name }

        return projectNames.map {
            serviceAccountClient.getStorageGridObjectAreas(it)
        }.flatten()
    }
}
