package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentController
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationdeploymentBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findApplicationDataByApplicationDeploymentId(_) >> createApplicationData()
    }
    def controller = new ApplicationDeploymentController(applicationDataService)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def applicationDeployment = response('$', Map)

    new ApplicationData('', '', new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        applicationDeployment.version.deployTag, applicationDeployment.namespace, applicationDeployment.namespace,
        applicationDeployment.affiliation, '', '', [],
        new ImageDetails('', Instant.now(), [:]), new DeployDetails('', 0, 0), [], '', null,
           new ApplicationDeploymentCommand(
            new ApplicationDeploymentRef(applicationDeployment.environment, ""),
            new AuroraConfigRef(applicationDeployment.affiliation, "master", "123"),
            [:]
        )
    )

  }
}
