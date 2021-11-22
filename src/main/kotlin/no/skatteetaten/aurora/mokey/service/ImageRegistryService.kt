package no.skatteetaten.aurora.mokey.service

import kotlinx.coroutines.reactive.awaitFirst
import org.springframework.stereotype.Service

@Service
class ImageRegistryService(
    val imageRegistryClient: ImageRegistryClient,
) {
    suspend fun findTagsByName(
        tagUrls: List<String>
    ): List<ImageTagResource> = imageRegistryClient.post<ImageTagResource>(
        "/manifest",
        TagUrlsWrapper(tagUrls),
    ).collectList()
        .awaitFirst()
}
