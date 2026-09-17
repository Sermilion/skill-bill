package skillbill.infrastructure.fs.contracts

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import skillbill.error.ShellContentContractException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException

internal object ClasspathContractSchemaLoader {
  private val jsonSchemaFactory: JsonSchemaFactory =
    JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
  private val objectMapper: ObjectMapper = ObjectMapper()
  private val yamlMapper: YAMLMapper = YAMLMapper()
  private val compiledSchemas: ConcurrentHashMap<String, JsonSchema> = ConcurrentHashMap()

  fun sharedObjectMapper(): ObjectMapper = objectMapper

  fun sharedYamlMapper(): YAMLMapper = yamlMapper

  fun readClasspathYamlText(
    classLoader: ClassLoader,
    resource: String,
    missingError: () -> ShellContentContractException,
  ): String {
    val stream = classLoader.getResourceAsStream(resource.removePrefix("/")) ?: throw missingError()
    return stream.use { it.readBytes().toString(Charsets.UTF_8) }
  }

  fun readClasspathYamlNode(
    classLoader: ClassLoader,
    resource: String,
    missingError: () -> ShellContentContractException,
  ): JsonNode {
    val text = readClasspathYamlText(classLoader, resource, missingError)
    return yamlMapper.readTree(text)
  }

  fun readValidatedClasspathYamlNode(
    classLoader: ClassLoader,
    resource: String,
    missingResource: () -> ShellContentContractException,
    processingFailure: (Throwable) -> ShellContentContractException,
    expectedSchemaId: String,
    expectedContractVersion: String,
    contractVersionPath: List<String> = listOf("properties", "contract_version", "const"),
    contractVersionMatches: ((JsonNode, String) -> Boolean)? = null,
    identityFailure: (String) -> ShellContentContractException,
  ): JsonNode = try {
    val node = readClasspathYamlNode(classLoader, resource, missingResource)
    validateIdentity(
      yamlNode = node,
      classpathResource = resource,
      expectedSchemaId = expectedSchemaId,
      expectedContractVersion = expectedContractVersion,
      contractVersionPath = contractVersionPath,
      contractVersionMatches = contractVersionMatches,
      identityFailure = identityFailure,
    )
    node
  } catch (cancellation: CancellationException) {
    throw cancellation
  } catch (error: ShellContentContractException) {
    throw error
  } catch (error: Exception) {
    throw processingFailure(error)
  }

  fun compiledSchema(
    cacheKey: String,
    classLoader: ClassLoader,
    classpathResource: String,
    missingResource: () -> ShellContentContractException,
    processingFailure: (Throwable) -> ShellContentContractException,
    loadFailureLogger: (Throwable) -> Unit,
    expectedSchemaId: String,
    expectedContractVersion: String,
    contractVersionPath: List<String> = listOf("properties", "contract_version", "const"),
    contractVersionMatches: ((JsonNode, String) -> Boolean)? = null,
    identityFailure: (String) -> ShellContentContractException,
    prepareSchemaDocument: (JsonNode) -> Unit = {},
  ): JsonSchema =
    compiledSchemas.computeIfAbsent(cacheKey) {
      compileSchemaDocument(
        classLoader = classLoader,
        classpathResource = classpathResource,
        missingResource = missingResource,
        processingFailure = processingFailure,
        loadFailureLogger = loadFailureLogger,
        expectedSchemaId = expectedSchemaId,
        expectedContractVersion = expectedContractVersion,
        contractVersionPath = contractVersionPath,
        contractVersionMatches = contractVersionMatches,
        identityFailure = identityFailure,
        prepareSchemaDocument = prepareSchemaDocument,
      )
    }

  fun compiledSchemaFromYamlNode(
    cacheKey: String,
    yamlNode: JsonNode,
    processingFailure: (Throwable) -> ShellContentContractException,
  ): JsonSchema =
    compiledSchemas.computeIfAbsent(cacheKey) {
      try {
        jsonSchemaFactory.getSchema(objectMapper.writeValueAsString(yamlNode), LOCALE_STABLE_SCHEMA_CONFIG)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Exception) {
        throw processingFailure(error)
      }
    }

  fun compileUncachedYamlNode(yamlNode: JsonNode): JsonSchema =
    jsonSchemaFactory.getSchema(objectMapper.writeValueAsString(yamlNode), LOCALE_STABLE_SCHEMA_CONFIG)

  fun validate(schema: JsonSchema, instance: JsonNode): Set<ValidationMessage> = schema.validate(instance)

  fun valueToTree(map: Map<String, Any?>): JsonNode = objectMapper.valueToTree(map)

  fun validateSchemaIdentity(
    yamlNode: JsonNode,
    classpathResource: String,
    expectedSchemaId: String,
    expectedContractVersion: String,
    identityFailure: (String) -> ShellContentContractException,
    contractVersionPath: List<String> = listOf("properties", "contract_version", "const"),
    contractVersionMatches: ((JsonNode, String) -> Boolean)? = null,
  ) = validateIdentity(
    yamlNode = yamlNode,
    classpathResource = classpathResource,
    expectedSchemaId = expectedSchemaId,
    expectedContractVersion = expectedContractVersion,
    contractVersionPath = contractVersionPath,
    contractVersionMatches = contractVersionMatches,
    identityFailure = identityFailure,
  )

  private fun compileSchemaDocument(
    classLoader: ClassLoader,
    classpathResource: String,
    missingResource: () -> ShellContentContractException,
    processingFailure: (Throwable) -> ShellContentContractException,
    loadFailureLogger: (Throwable) -> Unit,
    expectedSchemaId: String,
    expectedContractVersion: String,
    contractVersionPath: List<String>,
    contractVersionMatches: ((JsonNode, String) -> Boolean)?,
    identityFailure: (String) -> ShellContentContractException,
    prepareSchemaDocument: (JsonNode) -> Unit,
  ): JsonSchema {
    try {
      val yamlText = readClasspathYamlText(classLoader, classpathResource, missingResource)
      val yamlNode = yamlMapper.readTree(yamlText)
      validateIdentity(
        yamlNode = yamlNode,
        classpathResource = classpathResource,
        expectedSchemaId = expectedSchemaId,
        expectedContractVersion = expectedContractVersion,
        contractVersionPath = contractVersionPath,
        contractVersionMatches = contractVersionMatches,
        identityFailure = identityFailure,
      )
      prepareSchemaDocument(yamlNode)
      return jsonSchemaFactory.getSchema(objectMapper.writeValueAsString(yamlNode), LOCALE_STABLE_SCHEMA_CONFIG)
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: ShellContentContractException) {
      loadFailureLogger(error)
      throw error
    } catch (error: Exception) {
      val wrapped = processingFailure(error)
      loadFailureLogger(wrapped)
      throw wrapped
    }
  }

  private fun validateIdentity(
    yamlNode: JsonNode,
    classpathResource: String,
    expectedSchemaId: String,
    expectedContractVersion: String,
    contractVersionPath: List<String>,
    contractVersionMatches: ((JsonNode, String) -> Boolean)?,
    identityFailure: (String) -> ShellContentContractException,
  ) {
    val loadedId = yamlNode.path("\$id").asText("")
    if (loadedId != expectedSchemaId) {
      throw identityFailure(
        "Canonical schema identity mismatch for '$classpathResource': loaded '\$id' is '$loadedId' " +
          "but expected '$expectedSchemaId'.",
      )
    }
    val loadedVersion = contractVersionPath.fold(yamlNode) { node, segment -> node.path(segment) }.asText("")
    val versionMatches = contractVersionMatches?.invoke(yamlNode, expectedContractVersion)
      ?: (loadedVersion == expectedContractVersion)
    if (!versionMatches) {
      throw identityFailure(
        "Canonical schema contract version mismatch for '$classpathResource': loaded '$loadedVersion' " +
          "but expected '$expectedContractVersion'.",
      )
    }
  }
}
