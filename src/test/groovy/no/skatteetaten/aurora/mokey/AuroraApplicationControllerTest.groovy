package no.skatteetaten.aurora.mokey

import static org.springframework.restdocs.payload.JsonFieldType.BOOLEAN
import static org.springframework.restdocs.payload.JsonFieldType.NUMBER
import static org.springframework.restdocs.payload.JsonFieldType.OBJECT
import static org.springframework.restdocs.payload.JsonFieldType.STRING
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters

import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import no.skatteetaten.aurora.mokey.controller.AuroraApplicationController
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService
import no.skatteetaten.aurora.mokey.service.OpenShiftService

class AuroraApplicationControllerTest extends AbstractControllerTest {

  def NAMESPACE = "aurora"
  def APP = "reference"

  def mapper = new ObjectMapper().registerModule(new KotlinModule())

  def cacheService = Mock(AuroraApplicationCacheService)
  def openShiftService = Mock(OpenShiftService)

  def "Response for application"() {

    def app = mapper.readValue(loadResource("app.json"), AuroraApplication)
    cacheService.get("aurora/reference") >> app
    openShiftService.currentUserHasAccess(NAMESPACE) >> true

    when:
      ResultActions result = mockMvc.
          perform(
              RestDocumentationRequestBuilders.get('/aurora/namespace/{namespace}/application/{name}', NAMESPACE, APP))

    then:
      result
          .andExpect(MockMvcResultMatchers.status().isOk())
          .andDo(prettyDoc('application',
          pathParameters(
              parameterWithName("namespace").description("The namespace of the application"),
              parameterWithName("name").description("The name of the application")
          ),
          relaxedResponseFields(
              fieldWithPath("name").type(STRING).description("The name of the application"),
              fieldWithPath("namespace").type(STRING).description("The namespace/project the application is running in"),
              fieldWithPath("affiliation").type(STRING).optional().description("The affiliation the namespace belongs to"),
              fieldWithPath("targetReplicas").type(NUMBER).description("Desired number of replicas"),
              fieldWithPath("availableReplicas").type(NUMBER).description("Number of replicas that is available"),
              fieldWithPath("managementPath").type(STRING).optional().description("The path to the managementInterface root"),
              fieldWithPath("deploymentPhase").type(STRING).description("The path to the managementInterface root"),
              fieldWithPath("routeUrl").type(STRING).optional().description("The url to the route if any"),
              fieldWithPath("sprocketDone").type(STRING).optional().description("Status of Sprocket. Controller used for old db/sts integration"),
              fieldWithPath("pods[].name").type(STRING).description("Name of pod"),
              fieldWithPath("pods[].status").type(STRING).description("Status of pod"),
              fieldWithPath("pods[].restartCount").type(NUMBER).description("Number of times the pod has restarted"),
              fieldWithPath("pods[].podIP").type(STRING).description("IP address of pod"),
              fieldWithPath("pods[].startTime").type(STRING).description("Time the pod was started in zulu format."),
              fieldWithPath("pods[].deployment").type(STRING).optional().description("The deployment this pod was created in"),
              fieldWithPath("pods[].info").type(OBJECT).optional().description("An json object that represents the INFO enpoint from the pod"),
              fieldWithPath("pods[].health").type(OBJECT).optional().description("An json object that represents the HEALTH enpoint from the management interface"),
              fieldWithPath("pods[].links").type(OBJECT).optional().description("An json object that represents the links in the management interface"),
              fieldWithPath("pods[].ready").type(BOOLEAN).description("Is this pod ready to receive requests"),
              fieldWithPath("imageStream.deployTag").type(STRING).description("The deploy tag show the strategy used for deploying new versions of this application"),
              fieldWithPath("imageStream.registryUrl").type(STRING).description("URL to Docker registry for the image"),
              fieldWithPath("imageStream.group").type(STRING).description("What group/user in the docker registry is this application in "),
              fieldWithPath("imageStream.name").type(STRING).description("The name of the application in the docker registry"),
              fieldWithPath("imageStream.tag").type(STRING).description("The tag/version of the docker image that we update from"),
              fieldWithPath("imageStream.env").type(OBJECT).optional().description("ENV variables from the image in the registry")
          )
      ))
  }

  @Override
  protected List<Object> getControllersUnderTest() {

    [new AuroraApplicationController(cacheService, openShiftService)]
  }
}