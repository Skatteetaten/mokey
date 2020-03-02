package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.stereotype.Service

// TODO: Do we really need a seperate service for this?
@Service
class ImageRegistryService(
    val imageRegistryClient: ImageRegistryClient
) {
    // TODO: do not block, cache, retry
    suspend fun findTagsByName(
        tagUrls: List<String>
    ): List<ImageTagResource> {
        return imageRegistryClient.post<ImageTagResource>("/manifest", TagUrlsWrapper(tagUrls)).collectList()
            .awaitFirst()
    }
}
