package no.skatteetaten.aurora.mokey.service

import java.time.Duration
import org.springframework.stereotype.Service

@Service
class ImageRegistryService(
    val imageRegistryClient: ImageRegistryClient
) {
    fun findTagsByName(
        tagUrls: List<String>
    ): List<ImageTagResource> {
        return imageRegistryClient.post<ImageTagResource>("/manifest", TagUrlsWrapper(tagUrls))
            .collectList()
            .block(Duration.ofSeconds(5))!!
    }
}
