package no.skatteetaten.aurora.mokey.extensions

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.MissingNode
import com.fasterxml.jackson.databind.node.ObjectNode

fun JsonNode?.asMap(): Map<String, JsonNode> {
    if (this == null) {
        return emptyMap()
    }

    if (this is ObjectNode) {
        val fields = java.util.HashMap<String, JsonNode>()
        this.fields().forEachRemaining { entry -> fields.put(entry.key, entry.value) }
        return fields
    }
    return emptyMap()
}

fun JsonNode.extract(vararg pathAlternatives: String): JsonNode? {
    var valueNode: JsonNode = MissingNode.getInstance()
    for (alt in pathAlternatives) {
        valueNode = this.at(alt)
        if (valueNode !is MissingNode) {
            break
        }

        valueNode = this.getExact(alt)
        if (valueNode !is MissingNode) {
            break
        }
    }
    return if (valueNode !is MissingNode) valueNode else null
}

/**
 * This is for fields that contains slash in key. Ex: "commit.time/v1"
 * @return value for key if key exists or MissingNode if not.
 */
fun JsonNode.getExact(path: String): JsonNode {
    val value = try {
        this.fields().asSequence().first { "/${it.key}" == path }?.value
    } catch (ex: Exception) {
        null
    }
    return value ?: MissingNode.getInstance()
}
