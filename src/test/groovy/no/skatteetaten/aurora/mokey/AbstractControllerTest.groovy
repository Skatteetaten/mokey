package no.skatteetaten.aurora.mokey

import org.junit.Rule
import org.springframework.restdocs.JUnitRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler
import org.springframework.restdocs.operation.preprocess.Preprocessors
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders

import io.micrometer.spring.web.servlet.WebMvcMetrics
import no.skatteetaten.aurora.mokey.controller.ErrorHandler
import no.skatteetaten.aurora.mokey.controller.internal.ErrorHandler
import spock.lang.Specification

abstract class AbstractControllerTest extends Specification {

  @Rule
  JUnitRestDocumentation jUnitRestDocumentation = new JUnitRestDocumentation("build/docs/generated-snippets");

  MockMvc mockMvc

  def setup() {

    def controllers = []
    controllers.addAll(controllersUnderTest)
    mockMvc = MockMvcBuilders.standaloneSetup(controllers.toArray())
        .setControllerAdvice(new ErrorHandler(Mock(WebMvcMetrics)))
        .apply(MockMvcRestDocumentation.documentationConfiguration(jUnitRestDocumentation).uris()
        .withScheme("http")
        .withHost("mokey-aurora.utv.paas.skead.no")
        .withPort(80)
    ).build()
  }

  protected static RestDocumentationResultHandler prettyDoc(String identifier, Snippet... snippets) {
    MockMvcRestDocumentation.
        document(identifier, Preprocessors.preprocessRequest(Preprocessors.prettyPrint()), Preprocessors.
            preprocessResponse(Preprocessors.prettyPrint()), snippets)
  }

  protected abstract List<Object> getControllersUnderTest()
}
