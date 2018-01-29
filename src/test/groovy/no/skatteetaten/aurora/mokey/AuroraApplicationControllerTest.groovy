package no.skatteetaten.aurora.mokey

import static org.springframework.restdocs.payload.JsonFieldType.STRING
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

import groovy.json.JsonSlurper
import io.micrometer.spring.web.servlet.DefaultWebMvcTagsProvider
import io.micrometer.spring.web.servlet.WebMvcMetrics
import no.skatteetaten.aurora.AuroraMetrics
import no.skatteetaten.aurora.mokey.controller.AuroraApplicationController
import no.skatteetaten.aurora.mokey.model.AuroraApplication
import no.skatteetaten.aurora.mokey.service.AuroraApplicationCacheService

@SpringBootTest(classes = [Config,
    AuroraMetrics,
    Main,
    WebMvcMetrics,
    DefaultWebMvcTagsProvider], webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AuroraApplicationControllerTest extends AbstractControllerTest {

  static class Config {

  }

  def NAMESPACE="aurora"
  def APP="reference"

  @Autowired
  ObjectMapper mapper

  def cacheService = Mock(AuroraApplicationCacheService)

  def "Response for application"() {

    def app= new JsonSlurper().parse(new File(loadResource("app.json"))) as AuroraApplication
    cacheService.get("aurora/reference") >> app

    when:
      ResultActions result = mockMvc.
          perform(RestDocumentationRequestBuilders.get('/aurora/application/{namespace}/{name}', NAMESPACE, APP))

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

    [new AuroraApplicationController(cacheService)]
  }
}