package skillbill.infrastructure.launcher.codegraph

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import skillbill.contracts.codegraph.CodeGraphMcpKeys as K

internal object CodeGraphMcpProtocol {
  val mapper = ObjectMapper()
  const val PROTOCOL = "2025-03-26"
  fun request(id: Int, method: String, params: JsonNode = mapper.createObjectNode()): ObjectNode =
    mapper.createObjectNode().put(K.JSONRPC, "2.0").put(K.ID, id).put(K.METHOD, method)
      .set<ObjectNode>(K.PARAMS, params)

  fun result(id: JsonNode, body: JsonNode): ObjectNode = mapper.createObjectNode()
    .put(K.JSONRPC, "2.0").set<ObjectNode>(K.ID, id).set<ObjectNode>(K.RESULT, body)

  fun fallback(id: JsonNode, detail: String): ObjectNode {
    val body = mapper.createObjectNode().put(K.IS_ERROR, true)
    body.putArray(K.CONTENT).addObject().put(K.TYPE, "text").put(K.TEXT, detail)
    return result(id, body)
  }

  fun initialize(): ObjectNode = mapper.createObjectNode().put(K.PROTOCOL_VERSION, PROTOCOL).also {
    it.putObject(K.CAPABILITIES)
    it.putObject(K.CLIENT_INFO).put(K.NAME, "skill-bill").put(K.VERSION, "1")
  }
}
