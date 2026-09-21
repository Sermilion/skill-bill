package skillbill.infrastructure.contracts.experiment.codegraph
import skillbill.contracts.experiment.codegraph.CODEGRAPH_DEPENDENCY_CONTRACT_VERSION
import skillbill.contracts.experiment.codegraph.CODEGRAPH_QUERY_OUTPUT_CONTRACT_VERSION
import skillbill.contracts.experiment.codegraph.CODEGRAPH_QUERY_RECEIPT_CONTRACT_VERSION
import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.contracts.experiment.codegraph.CodeGraphDependencySchemaPaths
import skillbill.contracts.experiment.codegraph.CodeGraphQueryOutputSchemaPaths
import skillbill.contracts.experiment.codegraph.CodeGraphQueryReceiptSchemaPaths
import skillbill.error.shellcontent.InvalidCodeGraphDependencySchemaError
import skillbill.error.shellcontent.InvalidCodeGraphQueryOutputSchemaError
import skillbill.error.shellcontent.InvalidCodeGraphQueryReceiptSchemaError
import skillbill.error.shellcontent.ShellContentContractException
import skillbill.infrastructure.contracts.ClasspathContractSchemaLoader
import skillbill.infrastructure.contracts.CompiledSchemaRequest
import java.net.URI

private const val MAX_REPORTED_SCHEMA_FAILURES = 3

private data class CodeGraphSchemaValidationRequest(
  val payload: Any?,
  val classpathResource: String,
  val expectedId: String,
  val expectedContractVersion: String,
  val error: (String) -> ShellContentContractException,
)

object CodeGraphDependencySchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    CodeGraphSchemaValidationRequest(
      payload = payload,
      classpathResource = CodeGraphDependencySchemaPaths.CLASSPATH_RESOURCE,
      expectedId = CodeGraphDependencySchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = CODEGRAPH_DEPENDENCY_CONTRACT_VERSION,
      error = { reason -> InvalidCodeGraphDependencySchemaError(sourceLabel, reason) },
    ),
  )

  fun validateDownloadPolicy(payload: Map<String, Any?>, sourceLabel: String) {
    validate(payload, sourceLabel)
    val hosts: List<String> = (payload[CodeGraphDependencyPayloadKeys.ALLOWED_DOWNLOAD_HOSTS] as? List<*>)
      ?.filterIsInstance<String>()
      ?: emptyList()
    val assets = payload[CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS] as? List<*> ?: emptyList<Any?>()
    val failure = assets
      .filterIsInstance<Map<*, *>>()
      .mapNotNull { asset -> downloadPolicyFailure(asset, hosts) }
      .firstOrNull()
    if (failure != null) throw InvalidCodeGraphDependencySchemaError(sourceLabel, failure)
  }

  private fun downloadPolicyFailure(asset: Map<*, *>, hosts: List<String>): String? {
    val url = asset[CodeGraphDependencyPayloadKeys.DOWNLOAD_URL]?.toString()?.trim().orEmpty()
    if (url.contains("/releases/latest", ignoreCase = true)) {
      return "download URL must not use /releases/latest."
    }
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull()
    if (host == null || host !in hosts.map(String::lowercase)) {
      return "download host '$host' is not listed in allowed_download_hosts."
    }
    val digest = asset[CodeGraphDependencyPayloadKeys.SHA256]?.toString()?.trim().orEmpty()
    return "platform asset is missing a sha256 digest.".takeIf {
      !digest.matches(Regex("^[a-f0-9]{64}$"))
    }
  }
}

object CodeGraphQueryOutputSchemaValidator {
  fun validate(payload: Any?, sourceLabel: String) = validateAgainst(
    CodeGraphSchemaValidationRequest(
      payload = payload,
      classpathResource = CodeGraphQueryOutputSchemaPaths.CLASSPATH_RESOURCE,
      expectedId = CodeGraphQueryOutputSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = CODEGRAPH_QUERY_OUTPUT_CONTRACT_VERSION,
      error = { reason -> InvalidCodeGraphQueryOutputSchemaError(sourceLabel, reason) },
    ),
  )
}

object CodeGraphQueryReceiptSchemaValidator {
  fun validate(payload: Map<String, Any?>, sourceLabel: String) = validateAgainst(
    CodeGraphSchemaValidationRequest(
      payload = payload,
      classpathResource = CodeGraphQueryReceiptSchemaPaths.CLASSPATH_RESOURCE,
      expectedId = CodeGraphQueryReceiptSchemaPaths.EXPECTED_SCHEMA_ID,
      expectedContractVersion = CODEGRAPH_QUERY_RECEIPT_CONTRACT_VERSION,
      error = { reason -> InvalidCodeGraphQueryReceiptSchemaError(sourceLabel, reason) },
    ),
  )
}

private fun validateAgainst(request: CodeGraphSchemaValidationRequest) {
  val schema = ClasspathContractSchemaLoader.compiledSchema(
    CompiledSchemaRequest(
      cacheKey = request.classpathResource,
      classLoader = CodeGraphDependencySchemaValidator::class.java.classLoader,
      classpathResource = request.classpathResource,
      missingResource = { request.error("Canonical runtime contract is missing at '${request.classpathResource}'.") },
      processingFailure = { cause -> request.error(cause.message ?: cause::class.simpleName.orEmpty()) },
      loadFailureLogger = {},
      expectedSchemaId = request.expectedId,
      expectedContractVersion = request.expectedContractVersion,
      identityFailure = request.error,
    ),
  )
  val instance = ClasspathContractSchemaLoader.valueToTree(request.payload)
  val failures = schema.validate(instance).take(MAX_REPORTED_SCHEMA_FAILURES).toList()
  if (failures.isNotEmpty()) {
    throw request.error(failures.joinToString("; ") { it.message })
  }
}
