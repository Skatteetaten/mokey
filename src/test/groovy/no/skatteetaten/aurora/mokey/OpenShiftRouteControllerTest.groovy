package no.skatteetaten.aurora.mokey

import static org.springframework.restdocs.payload.JsonFieldType.STRING
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import static org.springframework.restdocs.payload.PayloadDocumentation.relaxedResponseFields
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
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
          .andDo(prettyDoc('route-exists-get',
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

  def "Response for nonexisting route"() {

    routeService.findRoute(_, _) >> null
    when:
      ResultActions result = mockMvc.
          perform(RestDocumentationRequestBuilders.get('/api/route/{namespace}/{name}', NAMESPACE, ROUTE))

    then:
      result
          .andExpect(MockMvcResultMatchers.status().isNotFound())
          .andDo(
          prettyDoc('route-notexists-get',
              relaxedResponseFields(
                  fieldWithPath("errorMessage").type(STRING).description("The error message describing what went wrong")
              )))
  }

  @Override
  protected List<Object> getControllersUnderTest() {

    [new OpenShiftRouteController(routeService)]
  }
}
