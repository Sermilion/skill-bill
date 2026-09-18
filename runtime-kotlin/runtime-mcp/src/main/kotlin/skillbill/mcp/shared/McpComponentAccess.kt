package skillbill.mcp.shared

internal fun componentForLegacyContext(value: Any): McpComponent {
  val method = value.javaClass.declaredMethods.firstOrNull {
    it.name == "mcpComponent" || it.name.startsWith("mcpComponent$")
  } ?: throw NoSuchMethodException("${value.javaClass.name}.mcpComponent")
  method.isAccessible = true
  return method.invoke(value) as McpComponent
}
