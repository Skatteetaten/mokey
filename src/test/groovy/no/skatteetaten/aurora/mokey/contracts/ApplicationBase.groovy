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
      findApplicationDataByApplicationId(_ as String) >> createApplicationData()
    }
    def controller = new ApplicationController(applicationDataService)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def applicationId = response('$.appId')
    def name = response('$.name')
    def affiliation = response('$.applicationInstances[0].affiliation')
    def namespace = response('$.applicationInstances[0].namespace')
    new ApplicationData(applicationId, '', new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        '', name, namespace, affiliation, '', '',
        [], null, new DeployDetails('', 1, 1), [], '', null)
  }
}
