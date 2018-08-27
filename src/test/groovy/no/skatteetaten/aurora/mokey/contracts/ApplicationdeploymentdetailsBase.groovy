package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsController
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResourceAssembler
import no.skatteetaten.aurora.mokey.controller.LinkBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.AuroraConfigRef
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

class ApplicationdeploymentdetailsBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllApplicationData(_ as List) >> [createApplicationData()]
      findApplicationDataByApplicationDeploymentId(_ as String) >> createApplicationData()
    }
    def assembler = new ApplicationDeploymentDetailsResourceAssembler(new LinkBuilder('http://localhost', [:]))
    def controller = new ApplicationDeploymentDetailsController(applicationDataService, assembler)
    setupMockMvc(controller)
  }

  ApplicationData createApplicationData() {
    def imageBuildTime = response('$.imageDetails.imageBuildTime')
    def dockerImageReference = response('$.imageDetails.dockerImageReference')
    def commitTime = response('$.gitInfo.commitTime')
    def buildTime = response('$.buildTime')

    def applicationName = response('$._embedded.Application.name')
    def applicationDeployment = response('$._embedded.Application.applicationDeployments[0]', Map)

    def details = response('$.podResources[0]', Map)
    def podDetails = new PodDetails(
        new OpenShiftPodExcerpt(details.name, details.status, details.restartCount, details.ready,
            '', details.startTime, ''),
        new Right(new ManagementData(null,
            new Right(new InfoResponse(['metrics': details._links.metrics.href], [:], [:], '',
                Instant.parse(commitTime), Instant.parse(buildTime))), new Right())))


    new ApplicationData('', '',
        new AuroraStatus(AuroraStatusLevel.HEALTHY, ''),
        applicationDeployment.version.deployTag,
        applicationName,
        applicationDeployment.namespace,
        applicationDeployment.affiliation,
        '',
        '',
        [podDetails],
        new ImageDetails(dockerImageReference, Instant.parse(imageBuildTime), [:]),
        new DeployDetails('', 1, 1), [],
        '',
        null,
        new ApplicationDeploymentCommand(
            [:],
            new ApplicationDeploymentRef(applicationDeployment.environment, applicationName),
            new AuroraConfigRef(applicationDeployment.affiliation, "master", "123"),
        )
    )
  }
}
