package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationInstanceController
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.mokey.service.AuroraStatuses

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
    new ApplicationData('', '', new AuroraStatuses([], [:]),
        applicationInstance.version.deployTag, applicationInstance.namespace, applicationInstance.namespace,
        applicationInstance.affiliation, '', '', [],
        new ImageDetails('', Instant.now(), [:]), new DeployDetails('', 0, 0), [], '', null)

  }
}
