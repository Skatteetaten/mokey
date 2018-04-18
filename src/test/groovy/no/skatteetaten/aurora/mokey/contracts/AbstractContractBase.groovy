package no.skatteetaten.aurora.mokey.contracts

import org.springframework.core.MethodParameter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.lang.Nullable
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath

import groovy.io.FileType
import io.restassured.module.mockmvc.RestAssuredMockMvc
import no.skatteetaten.aurora.mokey.controller.security.User
import spock.lang.Specification

abstract class AbstractContractBase extends Specification {
  protected Map<String, DocumentContext> jsonResponses = [:]

  void loadJsonResponses(def baseObject) {
    def baseName = baseObject.getClass().getSimpleName().toLowerCase().replaceFirst('spec$', '')
    def files = loadFiles(baseName)
    populateResponses(files)
  }

  private static loadFiles(String baseName) {
    def folderName = "/contracts/${baseName}/responses"
    def resource = getClass().getResource(folderName)
    if (resource == null) {
      return []
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

  def <T> T response(String responseName = jsonResponses.keySet().first(), String jsonPath, Class<T> type) {
    jsonResponses[responseName].read(jsonPath, type)
  }

  def setupMockMvc(Object controller) {
    def objectMapper = new ObjectMapper()
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    objectMapper.registerModule(new KotlinModule())
    objectMapper.registerModule(new JavaTimeModule())

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
