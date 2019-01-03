package no.skatteetaten.aurora.mokey.contracts

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.web.context.WebApplicationContext
import java.io.File

class ContractResponses(val objectMapper: ObjectMapper, val jsonResponses: Map<String, DocumentContext>) {
    inline fun <reified T : Any> response(responseName: String = jsonResponses.keys.first()): T {
        val json = jsonResponses[responseName]?.jsonString()
            ?: throw IllegalArgumentException("Invalid response name,  $responseName")
        return objectMapper.readValue(json)
    }
}

@ExtendWith(SpringExtension::class)
@SpringBootTest
abstract class ContractBase {

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    fun withContractResponses(baseTestObject: Any, fn: (responses: ContractResponses) -> Unit) {
        val responses = readJsonFiles(baseTestObject)
        fn(responses)
        RestAssuredMockMvc.webAppContextSetup(context)
    }

    private fun readJsonFiles(baseTestClass: Any): ContractResponses {
        val baseName = baseTestClass::class.simpleName
            ?: throw IllegalArgumentException("Invalid base object, ${baseTestClass::class.simpleName}")
        val folderName = "/contracts/${baseName.toLowerCase().removeSuffix("test")}/responses"
        val content = baseTestClass::class.java.getResource(folderName)

        val files = File(content.toURI()).walk().filter { it.name.endsWith(".json") }.toList()
        val jsonResponses = files.associateBy({ it.name.removeSuffix(".json") }, { JsonPath.parse(it) })
        return ContractResponses(objectMapper, jsonResponses)
    }
}
