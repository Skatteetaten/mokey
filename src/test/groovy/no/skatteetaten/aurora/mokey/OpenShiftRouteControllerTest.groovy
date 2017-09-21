package no.skatteetaten.aurora.mokey

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import static org.springframework.restdocs.payload.JsonFieldType.STRING
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.request.RequestDocumentation
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.web.client.RestTemplate

import no.skatteetaten.aurora.AuroraMetrics
import no.skatteetaten.aurora.annotations.AuroraApplication
import no.skatteetaten.aurora.mokey.controller.OpenShiftRouteController
import no.skatteetaten.aurora.mokey.service.Route
import no.skatteetaten.aurora.mokey.service.RouteService

@SpringBootTest(classes = [Config, AuroraMetrics, RestTemplate], webEnvironment = SpringBootTest.WebEnvironment.NONE)
class OpenShiftRouteControllerTest extends AbstractControllerTest {

  public static final String NAMESPACE = 'aurora'
  public static final String ROUTE = 'mokey'
  @AuroraApplication
  static class Config {}

  def routeService = Mock(RouteService)

  def "Response for existing route"() {

    routeService.findRoute(_, _) >> new Route(NAMESPACE, ROUTE)
    when:
      ResultActions result = mockMvc.
          perform(RestDocumentationRequestBuilders.get('/api/route/{namespace}/{name}', NAMESPACE, ROUTE))

    then:
      result
          .andExpect(MockMvcResultMatchers.status().isOk())
          .andDo(
          document('route-exists-get',
              Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
              RequestDocumentation.pathParameters(
                  parameterWithName("namespace").description("The namespace of the route"),
                  parameterWithName("name").description("The name of the route within the specified namespace")
              ),
              PayloadDocumentation.relaxedResponseFields(
                  fieldWithPath("route.namespace").type(STRING).description("The namespace of the requested route"),
                  fieldWithPath("route.name").type(STRING).description("The name of the requested route")
              )))
  }


  def "Response for nonexisting route"() {

    routeService.findRoute(_, _) >> null
    when:
      ResultActions result = mockMvc.
          perform(RestDocumentationRequestBuilders.get('/api/route/{namespace}/{name}', NAMESPACE, ROUTE))

    then:
      result
          .andExpect(MockMvcResultMatchers.status().isNotFound())
          .andDo(
          document('route-notexists-get',
              Preprocessors.preprocessResponse(Preprocessors.prettyPrint()),
              PayloadDocumentation.relaxedResponseFields(
                  fieldWithPath("errorMessage").type(STRING).description("The error message describing what went wrong")
              )))
  }

  @Override
  protected List<Object> getControllersUnderTest() {

    [new OpenShiftRouteController(routeService)]
  }
}
