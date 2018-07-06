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
import no.skatteetaten.aurora.mokey.model.InfoResponse
import no.skatteetaten.aurora.mokey.model.ManagementData
import no.skatteetaten.aurora.mokey.model.OpenShiftPodExcerpt
import no.skatteetaten.aurora.mokey.model.PodDetails
import no.skatteetaten.aurora.mokey.service.ApplicationDataService
import no.skatteetaten.aurora.utils.Right

class ApplicationinstancedetailsBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllApplicationData(_ as List) >> [createApplicationData()]
      findApplicationDataByInstanceId(_ as String) >> createApplicationData()
    }
    def assembler = new ApplicationInstanceDetailsResourceAssembler(new LinkBuilder('http://localhost', [:]))
    def controller = new ApplicationInstanceDetailsController(applicationDataService, assembler)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def imageBuildTime = response('$.imageDetails.imageBuildTime')
    def dockerImageReference = response('$.imageDetails.dockerImageReference')
    def commitTime = response('$.gitInfo.commitTime')
    def buildTime = response('$.buildTime')

    def applicationName = response('$._embedded.Application.name')
    def applicationInstance = response('$._embedded.Application.applicationInstances[0]', Map)

    def details = response('$.podResources[0]', Map)
    def podDetails = new PodDetails(
        new OpenShiftPodExcerpt(details.name, details.status, details.restartCount, details.ready,
            '', details.startTime, ''),
        new Right(new ManagementData(null,
            new Right(new InfoResponse(['metrics': details._links.metrics.href], [:], [:], '',
                Instant.parse(commitTime),Instant.parse(buildTime))), new Right())))


    new ApplicationData('', '',
        new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        applicationInstance.version.deployTag,
        applicationName,
        applicationInstance.namespace,
        applicationInstance.affiliation,
        '',
        '',
        [podDetails],
        new ImageDetails(dockerImageReference, Instant.parse(imageBuildTime), [:]),
        new DeployDetails('', 1, 1), [],
        '')
  }
}
