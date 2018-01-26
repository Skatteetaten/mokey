package no.skatteetaten.aurora.mokey.extensions

import com.fasterxml.jackson.databind.JsonNode
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

