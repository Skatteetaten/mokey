package no.skatteetaten.aurora.mokey.contracts

import org.springframework.core.MethodParameter
import org.springframework.hateoas.core.AnnotationRelProvider
import org.springframework.hateoas.hal.HalConfiguration
import org.springframework.hateoas.hal.Jackson2HalModule
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.lang.Nullable
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath

import groovy.io.FileType
import io.restassured.module.mockmvc.RestAssuredMockMvc
import no.skatteetaten.aurora.mokey.ObjectMapperConfigurer
import no.skatteetaten.aurora.mokey.controller.security.User
import spock.lang.Specification

abstract class AbstractContractBase extends Specification {
  protected Map<String, DocumentContext> jsonResponses = [:]
  private defaultResponseName = ''

  void loadJsonResponses(def baseObject) {
    def baseName = baseObject.getClass().getSimpleName().toLowerCase().replaceFirst('spec$', '')
    defaultResponseName = baseName
    def files = loadFiles(baseName)
    populateResponses(files)
  }

  private static loadFiles(String baseName) {
    def folderName = "/contracts/${baseName}/responses"
    def resource = getClass().getResource(folderName)
    if (resource == null) {
      throw new IllegalArgumentException("No json response files found for ${baseName}")
    }

    def files = []
    new File(resource.toURI()).eachFileMatch(FileType.FILES, ~/.*\.json/, {
      files.add(it)
    })
    return files
  }

  private List populateResponses(List files) {
    files.each {
      def name = it.name.replace('.json', '')
      def json = JsonPath.parse(it)
      jsonResponses.put(name, json)
    }
  }

  String response(String responseName = defaultResponseName, String jsonPath) {
    response(responseName, jsonPath, String)
  }

  /**
   * Read response value from json.
   * If no responseName is specified it will start by using the base folder name, for instance 'application'.
   * If no response is found with the specified name, it will try to load the first file.
   * This is useful if there is only one file in the responses folder.
   *
   * @param responseName
   * @param jsonPath
   * @param type
   * @return
   */
  def <T> T response(String responseName = defaultResponseName, String jsonPath, Class<T> type) {
    def responseValue = jsonResponses[responseName] ?: jsonResponses[jsonResponses.keySet().first()]
    responseValue.read(jsonPath, type)
  }

  def setupMockMvc(Object controller) {
    def objectMapper = ObjectMapperConfigurer.configureObjectMapper(new ObjectMapper())
    objectMapper.registerModule(new Jackson2HalModule())
    objectMapper.setHandlerInstantiator(
        new Jackson2HalModule.HalHandlerInstantiator(new AnnotationRelProvider(), null, null, new HalConfiguration()))


    def converter = new MappingJackson2HttpMessageConverter()
    converter.setObjectMapper(objectMapper)

    def mockMvcBuilder = MockMvcBuilders.standaloneSetup(controller)
        .setMessageConverters(converter)
        .setCustomArgumentResolvers(createArgumentResolverForUser())

    RestAssuredMockMvc.standaloneSetup(mockMvcBuilder)
  }

  private static HandlerMethodArgumentResolver createArgumentResolverForUser() {
    new HandlerMethodArgumentResolver() {

      @Override
      boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType().isAssignableFrom(User)
      }

      @Override
      Object resolveArgument(MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) throws Exception {
        return new User('username', 'token', 'fullName')
      }
    }
  }

}
