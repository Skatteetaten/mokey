package no.skatteetaten.aurora.mokey.service

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service


@Service
@Profile("openshift")
class AuroraApplicationCrawler(val openShiftService: OpenShiftService, val auroraApplicationCacheService: AuroraApplicationCacheService) {

    @Scheduled(fixedRate = 300000, initialDelay = 360)
    fun performLoad() {

        val projects = openShiftService.projects().map { it.metadata.name }
        auroraApplicationCacheService.load(projects)
    }
}