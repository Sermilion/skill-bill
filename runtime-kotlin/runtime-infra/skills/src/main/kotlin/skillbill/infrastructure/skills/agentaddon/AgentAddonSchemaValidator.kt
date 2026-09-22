package skillbill.infrastructure.skills.agentaddon

import com.fasterxml.jackson.databind.JsonNode
import com.networknt.schema.JsonSchema
import skillbill.contracts.agentaddon.AGENT_ADDON_CONTRACT_VERSION
import skillbill.contracts.agentaddon.AgentAddonSchemaPaths
import skillbill.error.shellcontent.InvalidAgentAddonSchemaError
import skillbill.infrastructure.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.contracts.SchemaIdentityRequest

private object ClasspathAgentAddonSchemaResourceLoader : AgentAddonSchemaResourceLoader {
  override fun read(): String =
    AgentAddonSchemaValidator::class.java.classLoader
      .getResourceAsStream(AgentAddonSchemaPaths.CLASSPATH_RESOURCE)
      ?.bufferedReader()
      ?.use { it.readText() }
      ?: throw InvalidAgentAddonSchemaError(
        AgentAddonSchemaPaths.CLASSPATH_RESOURCE,
        "canonical schema resource is missing",
      )
}

fun interface AgentAddonSchemaResourceLoader {
  fun read(): String
}

class AgentAddonSchemaValidator(
  private val resourceLoader: AgentAddonSchemaResourceLoader = ClasspathAgentAddonSchemaResourceLoader,
) {
  fun validate(
    manifest: Map<String, Any?>,
    sourceLabel: String,
  ) = schemaOperation(sourceLabel, "schema validation failed") {
    val errors = ClasspathContractSchemaLoader.validate(schema(), ClasspathContractSchemaLoader.valueToTree(manifest))
    if (errors.isNotEmpty()) {
      val reason = errors.sortedBy { it.instanceLocation.toString() }.joinToString("; ") { it.message }
      invalidSchema(sourceLabel, reason)
    }
  }

  private fun schema(): JsonSchema =
    schemaOperation(AgentAddonSchemaPaths.CLASSPATH_RESOURCE, "canonical schema cannot be loaded") {
      val source = AgentAddonSchemaPaths.CLASSPATH_RESOURCE
      val node: JsonNode = ClasspathContractSchemaLoader.sharedYamlMapper().readTree(resourceLoader.read())
      ClasspathContractSchemaLoader.validateSchemaIdentity(
        SchemaIdentityRequest(
          yamlNode = node,
          classpathResource = source,
          expectedSchemaId = AgentAddonSchemaPaths.EXPECTED_SCHEMA_ID,
          expectedContractVersion = AGENT_ADDON_CONTRACT_VERSION,
          identityFailure = { reason -> InvalidAgentAddonSchemaError(source, reason) },
        ),
      )
      ClasspathContractSchemaLoader.compiledSchemaFromYamlNode(
        cacheKey = "agent-addon:${System.identityHashCode(resourceLoader)}:$source",
        yamlNode = node,
        processingFailure = { cause ->
          InvalidAgentAddonSchemaError(source, cause.message ?: cause::class.simpleName.orEmpty(), cause)
        },
      )
    }
}

private inline fun <T> schemaOperation(
  sourceLabel: String,
  fallbackReason: String,
  operation: () -> T,
): T =
  runCatching(operation).getOrElse { error ->
    throw error.asAgentAddonSchemaError(sourceLabel, fallbackReason)
  }

private fun Throwable.asAgentAddonSchemaError(
  sourceLabel: String,
  fallbackReason: String,
): Throwable =
  when (this) {
    is InvalidAgentAddonSchemaError -> this
    is Exception -> InvalidAgentAddonSchemaError(sourceLabel, message ?: fallbackReason, this)
    else -> this
  }

private fun invalidSchema(
  sourceLabel: String,
  reason: String,
): Nothing = throw InvalidAgentAddonSchemaError(sourceLabel, reason)
