package no.skatteetaten.aurora.mokey.contracts

import java.time.Instant

import org.intellij.lang.annotations.Language

import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsController
import no.skatteetaten.aurora.mokey.controller.ApplicationDeploymentDetailsResourceAssembler
import no.skatteetaten.aurora.mokey.controller.LinkBuilder
import no.skatteetaten.aurora.mokey.model.ApplicationData
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentCommand
import no.skatteetaten.aurora.mokey.model.ApplicationDeploymentRef
import no.skatteetaten.aurora.mokey.model.ApplicationPublicData
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
import no.skatteetaten.aurora.mokey.service.ManagementEndpoint
import no.skatteetaten.aurora.utils.Right

class ApplicationdeploymentdetailsBase extends AbstractContractBase {

  void setup() {
    loadJsonResponses(this)
    def applicationDataService = Mock(ApplicationDataService) {
      findAllApplicationData(_ as List, _ as  List) >> [createApplicationData()]
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


    def details = response('$.podResources[0]', Map)
    @Language("JSON")
    def infoResponseJson = """{
  "git": {
    "build.time": \"${buildTime}",
    "commit.time": \"${commitTime}",
    "commit.id.abbrev": ""
  },
  "podLinks": {
    "metrics": "${details._links.metrics.href}"  
  }
}"""
    def infoResponse = ManagementEndpoint.toHttpResponse(infoResponseJson, InfoResponse)
    def podDetails = new PodDetails(
        new OpenShiftPodExcerpt(details.name as String, details.status as String, details.restartCount as Integer,
            details.ready as Boolean, '', details.startTime as String, ''),
        new Right(new ManagementData(null, new Right(infoResponse), new Right()))
    )

    def publicData = new ApplicationPublicData(
        "", "appDeploymentId", "", "",
        new AuroraStatus(AuroraStatusLevel.HEALTHY, "", []),
        "", "",
        "master-SNAPSHOT",
        "",
        null,
        "releaseTo",
        Instant.EPOCH
    )
    new ApplicationData(
        '',
        '',
        [podDetails],
        new ImageDetails(dockerImageReference, Instant.parse(imageBuildTime), [:]),
        new DeployDetails('', 1, 1), [],
        '',
        null,
        new ApplicationDeploymentCommand(
            [:],
            new ApplicationDeploymentRef("utv", "test"),
            new AuroraConfigRef("paas", "master", "123"),
        ), publicData
    )
  }
}
