package no.skatteetaten.aurora.mokey.contracts

import no.skatteetaten.aurora.mokey.controller.ApplicationController
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllApplicationData(_ as List) >> [createApplicationData()]
    }
    def controller = new ApplicationController(applicationDataService)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def affiliation = response('$[0].applicationInstances[0].affiliation')
    def name = response('$[0].name')
    def namespace = response('$[0].applicationInstances[0].namespace')
    new ApplicationData('', new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        '', name, namespace, affiliation, '', '',
        [], null, new DeployDetails('', 1, 1), [], '')
  }
}
