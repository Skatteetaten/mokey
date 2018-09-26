package no.skatteetaten.aurora.mokey.contracts

import no.skatteetaten.aurora.mokey.controller.ApplicationController
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllPublicApplicationData(_ as List) >> [createApplicationData()]
      findPublicApplicationDataByApplicationId(_ as String) >> createApplicationData()
    }
    def controller = new ApplicationController(applicationDataService)
    setupMockMvc(controller)
  }

  ApplicationPublicData createApplicationData() {
    def applicationId = response('$.identifier')
    def applicationDeploymentId = response('$.applicationDeployments[0].identifier')
    def name = response('$.name')
    def affiliation = response('$.applicationDeployments[0].affiliation')
    def namespace = response('$.applicationDeployments[0].namespace')
    new ApplicationPublicData(
        applicationId,
        applicationDeploymentId,
        name,
        name,
        new AuroraStatus(AuroraStatusLevel.HEALTHY, '', []),
        affiliation,
        namespace,
        "",
        null,
        null
    )
  }
}
