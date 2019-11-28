package no.skatteetaten.aurora.mokey.service

import org.springframework.stereotype.Service
import java.time.Duration

@Service
class ImageRegistryService(
    val imageRegistryClient: ImageRegistryClient
) {
    fun findTagsByName(
        tagUrls: List<String>
    ): AuroraResponse<ImageTagResource> {
        return imageRegistryClient.post<ImageTagResource>(
            "/manifest",
            TagUrlsWrapper(tagUrls)
        ).block(Duration.ofSeconds(5))!!
    }
}