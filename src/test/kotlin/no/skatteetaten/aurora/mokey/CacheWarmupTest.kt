package no.skatteetaten.aurora.mokey

import io.mockk.mockk
import io.mockk.verify
import no.skatteetaten.aurora.mokey.service.ApplicationDataServiceCacheDecorator
import org.junit.jupiter.api.Test

class CacheWarmupTest {

    @Test
    fun `Refresh cache when affiliation is configured`() {
        val applicationDataService = mockk<ApplicationDataServiceCacheDecorator>(relaxed = true)
        val cacheWarmup = CacheWarmup(applicationDataService, "paas")
        cacheWarmup.afterPropertiesSet()
        verify { applicationDataService.refreshCache(listOf("paas")) }
    }
}