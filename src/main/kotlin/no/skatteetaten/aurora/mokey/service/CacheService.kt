package no.skatteetaten.aurora.mokey.service

import no.skatteetaten.aurora.mokey.model.Environment

interface CacheService {
    /**
     * refreshCache refreshes the cache for the provided affiliations.
     * If affiliations is empty then the cache is refreshed for all affiliations.
     */
    suspend fun refreshCache(groupedAffiliations: Map<String, List<Environment>>)

    /**
     * refreshItem refreshes the cache for the given input parameter.
     */
    suspend fun refreshItem(applicationDeploymentId: String)

    /**
     * refreshResource fetches all resources in affiliation and environments.
     */
    suspend fun refreshResource(affiliation: String, env: List<Environment>)
}
