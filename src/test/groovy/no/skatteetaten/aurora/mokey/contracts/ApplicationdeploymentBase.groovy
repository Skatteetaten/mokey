package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentController
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationdeploymentBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findPublicApplicationDataByApplicationDeploymentId(_) >> createApplicationData()
    }
    def controller = new ApplicationDeploymentController(applicationDataService)
    setupMockMvc(controller)
  }

  ApplicationPublicData createApplicationData() {
    def applicationDeployment = response('$', Map)

    String applicationDeploymentId = applicationDeployment.identifier
    String applicationDeploymentName = applicationDeployment.name
    String deployTag = applicationDeployment.version.deployTag
    String namespace = applicationDeployment.namespace
    String affiliation = applicationDeployment.affiliation

    new ApplicationPublicData(
        "",
        applicationDeploymentId,
        "",
        applicationDeploymentName,
        new AuroraStatus(AuroraStatusLevel.HEALTHY, '', [] as Set),
        affiliation,
        namespace,
        deployTag,
        "",
        null,
        "releaseTo",
        Instant.EPOCH
    )
  }
}
