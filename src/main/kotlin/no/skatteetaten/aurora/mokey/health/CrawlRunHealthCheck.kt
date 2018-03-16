package no.skatteetaten.aurora.mokey.health

import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class CrawlRunHealthCheck(val cache: AuroraApplicationCacheService) : HealthIndicator {
    override fun health(): Health {

        if (cache.cachePopulated) {
            return Health.up().build()
        }

        return Health.down().withDetail("message", "Cache running").build()
    }
}