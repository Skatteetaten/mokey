package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationInstanceDetailsController
import no.skatteetaten.aurora.mokey.controller.ApplicationInstanceDetailsResourceAssembler
import no.skatteetaten.aurora.mokey.controller.LinkBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.AuroraStatus
import no.skatteetaten.aurora.mokey.model.AuroraStatusLevel
import no.skatteetaten.aurora.mokey.model.DeployDetails
import no.skatteetaten.aurora.mokey.model.ImageDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService

class ApplicationinstancedetailsBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllApplicationData(_ as List) >> [createApplicationData()]
      findApplicationDataById(_ as String) >> createApplicationData()
    }
    def assembler = new ApplicationInstanceDetailsResourceAssembler(new LinkBuilder('http://localhost', [:]))
    def controller = new ApplicationInstanceDetailsController(applicationDataService, assembler)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def imageBuildTime = response('applicationinstancedetails', '$.imageDetails.imageBuildTime', String)
    def dockerImageReference = response('applicationinstancedetails', '$.imageDetails.dockerImageReference', String)

    def applicationName = response('applicationinstancedetails', '$._embedded.Application.name', String)
    def applicationInstance =
        response('applicationinstancedetails', '$._embedded.Application.applicationInstances[0]n ', Map)

    new ApplicationData('', new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        applicationInstance.version.deployTag, applicationName, applicationInstance.namespace,
        applicationInstance.affiliation, '', '',
        [], new ImageDetails(dockerImageReference, Instant.parse(imageBuildTime), [:]), new DeployDetails('', 1, 1), [],
        '')
  }
}
