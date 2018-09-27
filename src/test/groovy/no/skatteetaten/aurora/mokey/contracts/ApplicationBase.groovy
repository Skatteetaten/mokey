package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationController
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
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
    def applicationId = response('$.identifier')
    def applicationDeploymentId = response('$.applicationDeployments[0].identifier')
    def name = response('$.name')
    def affiliation = response('$.applicationDeployments[0].affiliation')
    def namespace = response('$.applicationDeployments[0].namespace')
    new ApplicationData(applicationId, applicationDeploymentId, new AuroraStatus(AuroraStatusLevel.HEALTHY, '', []),
        '', name, name, namespace, affiliation, '', '',
        [], null, new DeployDetails('', 1, 1), [], '', null,
        new ApplicationDeploymentCommand(
            [:],
            new ApplicationDeploymentRef("", ""),
            new AuroraConfigRef("", "", ""),
        ),
        "releaseTo",
        Instant.EPOCH
    )
  }
}
