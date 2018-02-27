package no.skatteetaten.aurora.mokey.service

import io.fabric8.openshift.api.model.DeploymentConfigBuilder
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import spock.lang.Specification

class AuroraApplicationCacheSpec extends Specification {

  /*
  @Rule
  def server = new OpenShiftServer(true, true)
  def client = server.getOpenshiftClieknt()
  */

  def openshiftService = Mock(OpenShiftService)
  def applicationService = Mock(AuroraApplicationService)

  def service = new AuroraApplicationCacheService(openshiftService, applicationService)

  def project = "jedi"
  def name = "yoda"
  def dc = new DeploymentConfigBuilder()
      .withNewMetadata().withName(name).withNamespace(project).endMetadata()
      .build()

  def app = new AuroraApplication(name, project)

  def "should scrape deploymentConfigs and add cache"() {
    when:
      1 * openshiftService.deploymentConfigs(project) >> [dc]
      1 * applicationService.handleApplication(project, dc) >> app

      service.load([project])
    then:
      service.cachePopulated
      service.cache.size() == 1
      service.get("${project}/${name}") == app

  }

  def "should remove old applications when scraping for a second time"() {

    given:
      def name2 = "anakin"
      def project2 = "sith"
      def dc2 = new DeploymentConfigBuilder()
          .withNewMetadata().withName(name2).withNamespace(project2).endMetadata()
          .build()

      def app2 = new AuroraApplication(name2, project2)

    when:
      1 * openshiftService.deploymentConfigs(project) >> [dc]
      1 * applicationService.handleApplication(project, dc) >> app

      1 * openshiftService.deploymentConfigs(project2) >> [dc2]
      1 * applicationService.handleApplication(project2, dc2) >> app2
      service.load([project])
      service.load([project2])
    then:
      service.cachePopulated
      service.cache.size() == 1
      service.get("${project2}/${name2}") == app2

  }
}
