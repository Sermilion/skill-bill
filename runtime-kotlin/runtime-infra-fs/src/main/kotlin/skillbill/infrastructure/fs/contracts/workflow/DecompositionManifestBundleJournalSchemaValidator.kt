package skillbill.infrastructure.fs.contracts.workflow

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.networknt.schema.JsonSchema
import com.networknt.schema.ValidationMessage
import skillbill.contracts.JsonCodec
import skillbill.contracts.SharedPayloadKeys
import skillbill.contracts.decomposition.BUNDLE_JOURNAL_CONTRACT_VERSION
import skillbill.contracts.decomposition.DecompositionManifestBundleJournalSchemaPaths
import skillbill.error.InvalidDecompositionManifestBundleJournalError
import skillbill.infrastructure.fs.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.fs.contracts.CompiledSchemaRequest
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

object DecompositionManifestBundleJournalSchemaValidator {
  private val mapper: ObjectMapper
    get() = ClasspathContractSchemaLoader.sharedObjectMapper()
  private val yamlMapper: YAMLMapper =
    YAMLMapper(YAMLFactory().apply { enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION) })

  fun validateYamlText(yamlText: String, sourceLabel: String): Map<String, Any?> {
    val node = readYamlObjectNode(yamlText, sourceLabel)
    val parsed = yamlObjectNodeToMap(node, sourceLabel)
    validateMap(parsed, sourceLabel)
    return parsed
  }

  fun validateMap(manifest: Map<String, Any?>, sourceLabel: String) {
    val contractVersion = manifest[SharedPayloadKeys.CONTRACT_VERSION]
    if (contractVersion != BUNDLE_JOURNAL_CONTRACT_VERSION) {
      throw InvalidDecompositionManifestBundleJournalError(
        sourceLabel = sourceLabel,
        reason = "Unsupported contract_version '$contractVersion'. Supported version is " +
          "$BUNDLE_JOURNAL_CONTRACT_VERSION. Back up the marker and its staging directory, " +
          "review evidence manually, then remove the marker only after backup.",
        failureCode = "unsupported_contract_version",
      )
    }
    val instance: JsonNode = mapper.valueToTree(manifest)
    val errors: Set<ValidationMessage> = schema().validate(instance)
    if (errors.isNotEmpty()) {
      throw InvalidDecompositionManifestBundleJournalError(
        sourceLabel = sourceLabel,
        reason = errors.sortedBy { it.message }.joinToString("; ") { it.message },
        failureCode = "schema_invalid",
      )
    }
  }

  private fun readYamlObjectNode(yamlText: String, sourceLabel: String): JsonNode {
    val node = parseYamlNode(yamlText, sourceLabel)
    if (node == null || !node.isObject) {
      throw InvalidDecompositionManifestBundleJournalError(
        sourceLabel = sourceLabel,
        reason = "<root> must be an object.",
        failureCode = "root_not_object",
      )
    }
    return node
  }

  private fun parseYamlNode(yamlText: String, sourceLabel: String): JsonNode? = try {
    yamlMapper.factory.createParser(yamlText).use { parser ->
      val parsed = yamlMapper.readTree<JsonNode>(parser)
      require(parser.nextToken() == null) { "YAML contains trailing content or multiple documents." }
      parsed
    }
  } catch (error: CancellationException) {
    throw error
  } catch (error: IOException) {
    throw InvalidDecompositionManifestBundleJournalError(
      sourceLabel = sourceLabel,
      reason = error.message ?: "Malformed YAML.",
      failureCode = "yaml_parse_error",
      cause = error,
    )
  }

  private fun yamlObjectNodeToMap(node: JsonNode, sourceLabel: String): Map<String, Any?> = try {
    val converted = JsonCodec.anyToStringAnyMap(yamlMapper.convertValue(node, Map::class.java))
      ?: throw InvalidDecompositionManifestBundleJournalError(
        sourceLabel = sourceLabel,
        reason = "<root> must be an object.",
        failureCode = "root_not_object",
      )
    converted
  } catch (error: CancellationException) {
    throw error
  } catch (error: InvalidDecompositionManifestBundleJournalError) {
    throw error
  } catch (error: IllegalArgumentException) {
    throw InvalidDecompositionManifestBundleJournalError(
      sourceLabel = sourceLabel,
      reason = error.message ?: "Malformed YAML object.",
      failureCode = "yaml_object_error",
      cause = error,
    )
  }

  private fun schema(): JsonSchema = ClasspathContractSchemaLoader.compiledSchema(
    CompiledSchemaRequest(
      cacheKey = DecompositionManifestBundleJournalSchemaPaths.CLASSPATH_RESOURCE,
      classLoader = DecompositionManifestBundleJournalSchemaValidator::class.java.classLoader,
      classpathResource = DecompositionManifestBundleJournalSchemaPaths.CLASSPATH_RESOURCE,
      missingResource = {
        InvalidDecompositionManifestBundleJournalError(
          sourceLabel = DecompositionManifestBundleJournalSchemaPaths.CLASSPATH_RESOURCE,
          reason = "Canonical bundle journal schema resource is missing from the classpath.",
          failureCode = "schema_resource_missing",
        )
      },
      processingFailure = { cause ->
        InvalidDecompositionManifestBundleJournalError(
          sourceLabel = DecompositionManifestBundleJournalSchemaPaths.CLASSPATH_RESOURCE,
          reason = cause.message ?: cause::class.simpleName.orEmpty(),
          failureCode = "schema_load_error",
          cause = cause,
        )
      },
      loadFailureLogger = {},
      expectedSchemaId = DecompositionManifestBundleJournalSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = BUNDLE_JOURNAL_CONTRACT_VERSION,
      identityFailure = { reason ->
        InvalidDecompositionManifestBundleJournalError(
          sourceLabel = DecompositionManifestBundleJournalSchemaPaths.CLASSPATH_RESOURCE,
          reason = reason,
          failureCode = "schema_identity_error",
        )
      },
    ),
  )
}
