package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationInstanceController
import no.skatteetaten.aurora.mokey.model.ApplicationCommand
import no.skatteetaten.aurora.mokey.model.ApplicationCommandId
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationinstanceBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findApplicationDataByInstanceId(_) >> createApplicationData()
    }
    def controller = new ApplicationInstanceController(applicationDataService)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def applicationInstance = response('$', Map)

    new ApplicationData('', '', new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        applicationInstance.version.deployTag, applicationInstance.namespace, applicationInstance.namespace,
        applicationInstance.affiliation, '', '', [],
        new ImageDetails('', Instant.now(), [:]), new DeployDetails('', 0, 0), [], '', null,
           new ApplicationCommand(
            new ApplicationCommandId(applicationInstance.environment, ""),
            new AuroraConfigRef(applicationInstance.affiliation, "master"),
            [:]
        )
    )

  }
}
