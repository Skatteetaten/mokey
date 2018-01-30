package no.skatteetaten.aurora.mokey

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
              parameterWithName("namespace").description("The namespace of the route"),
              parameterWithName("name").description("The name of the route within the specified namespace")
          ),
          relaxedResponseFields(
              fieldWithPath("route.namespace").type(STRING).description("The namespace of the requested route"),
              fieldWithPath("route.name").type(STRING).description("The name of the requested route")
          )
      ))
  }

  @Override
  protected List<Object> getControllersUnderTest() {

    [new AuroraApplicationController(cacheService, openShiftService)]
  }
}