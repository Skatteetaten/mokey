package no.skatteetaten.aurora.mokey.contracts

import no.skatteetaten.aurora.mokey.controller.ApplicationDetailsController
import no.skatteetaten.aurora.mokey.controller.ApplicationDetailsResourceAssembler
import no.skatteetaten.aurora.mokey.controller.LinkBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationdetailsBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllApplicationData(_ as List) >> [createApplicationData()]
      findApplicationDataById(_ as String) >> createApplicationData()
    }
    def assembler = new ApplicationDetailsResourceAssembler(new LinkBuilder('http://localhost'))
    def controller = new ApplicationDetailsController(applicationDataService, assembler)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def application = response('applicationdetails', '$._embedded.Application', Map)
    new ApplicationData('', new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        application.version.deployTag, application.name, '', application.affiliation, '', '',
        [], null, new DeployDetails('', 1, 1), [], '')
  }
}
