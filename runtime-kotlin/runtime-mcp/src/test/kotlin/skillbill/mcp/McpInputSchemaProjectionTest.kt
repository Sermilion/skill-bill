package skillbill.mcp

import skillbill.contracts.JsonCodec
import skillbill.mcp.core.McpInputSchemaProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class McpInputSchemaProjectionTest {
  @Test
  fun `freeObjectShape ref projects to an inlined object schema without ref`() {
    val schema = McpInputSchemaProjection.projectedInputSchema("new_skill_scaffold")
    val payloadProperty = JsonCodec.anyToStringAnyMap(schema["properties"])?.get("payload")
    val payloadSchema = JsonCodec.anyToStringAnyMap(payloadProperty)
    assertNotNull(payloadSchema, "new_skill_scaffold.payload must be projected")
    assertEquals("object", payloadSchema["type"])
    assertFalse(payloadSchema.containsKey("\$ref"), "tools/list must not leak local \$ref keys")
  }
}
