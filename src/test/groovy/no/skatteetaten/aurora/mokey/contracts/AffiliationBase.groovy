package no.skatteetaten.aurora.mokey.contracts

import no.skatteetaten.aurora.mokey.controller.AffiliationController
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class AffiliationBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllAffiliations() >> response('$', List)
    }
    def controller = new AffiliationController(applicationDataService)
    setupMockMvc(controller)
  }
}
