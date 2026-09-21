package skillbill.infrastructure.contracts.experiment.codegraph
import skillbill.contracts.experiment.codegraph.CODEGRAPH_DEPENDENCY_CONTRACT_VERSION
import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.error.shellcontent.InvalidCodeGraphDependencySchemaError
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CodeGraphDependencySchemaValidationTest {
  @Test
  fun `latest download url loud-fails before install`() {
    val payload = governedDeclaration().toMutableMap()
    val assets = (payload[CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS] as List<*>)
      .filterIsInstance<Map<String, Any?>>()
      .map { asset ->
        if (asset[CodeGraphDependencyPayloadKeys.PLATFORM_ID] == "linux-x64") {
          asset.toMutableMap().apply {
            put(
              CodeGraphDependencyPayloadKeys.DOWNLOAD_URL,
              "https://github.com/colbymchenry/codegraph/releases/latest/download/codegraph-linux-x64.tar.gz",
            )
          }
        } else {
          asset
        }
      }
    payload[CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS] = assets
    assertFailsWith<InvalidCodeGraphDependencySchemaError> {
      CodeGraphDependencySchemaValidator.validateDownloadPolicy(payload, "test")
    }
  }

  @Test
  fun `missing digest loud-fails before install`() {
    val payload = governedDeclaration().toMutableMap()
    val assets = (payload[CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS] as List<*>)
      .filterIsInstance<Map<String, Any?>>()
      .map { asset ->
        if (asset[CodeGraphDependencyPayloadKeys.PLATFORM_ID] == "linux-x64") {
          asset.toMutableMap().apply { remove(CodeGraphDependencyPayloadKeys.SHA256) }
        } else {
          asset
        }
      }
    payload[CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS] = assets
    assertFailsWith<InvalidCodeGraphDependencySchemaError> {
      CodeGraphDependencySchemaValidator.validateDownloadPolicy(payload, "test")
    }
  }

  private fun governedDeclaration(): Map<String, Any?> = mapOf(
    CodeGraphDependencyPayloadKeys.CONTRACT_VERSION to CODEGRAPH_DEPENDENCY_CONTRACT_VERSION,
    CodeGraphDependencyPayloadKeys.UPSTREAM to mapOf(
      CodeGraphDependencyPayloadKeys.REPOSITORY to "colbymchenry/codegraph",
      CodeGraphDependencyPayloadKeys.RELEASE_TAG to "v1.6.0",
      CodeGraphDependencyPayloadKeys.LICENSE_SPDX to "MIT",
    ),
    CodeGraphDependencyPayloadKeys.ALLOWED_DOWNLOAD_HOSTS to listOf("github.com"),
    CodeGraphDependencyPayloadKeys.PLATFORM_ASSETS to listOf(
      mapOf(
        CodeGraphDependencyPayloadKeys.PLATFORM_ID to "linux-x64",
        CodeGraphDependencyPayloadKeys.ARCHIVE_KIND to "tar_gz",
        CodeGraphDependencyPayloadKeys.ASSET_NAME to "codegraph-linux-x64.tar.gz",
        CodeGraphDependencyPayloadKeys.DOWNLOAD_URL to
          "https://github.com/colbymchenry/codegraph/releases/download/v1.6.0/codegraph-linux-x64.tar.gz",
        CodeGraphDependencyPayloadKeys.SHA256 to
          "de3391f79ed42622d937e6cd5b7642a7ea8bb7d1473607e80b879ba73ef216b0",
      ),
    ),
    CodeGraphDependencyPayloadKeys.CLI to mapOf(
      CodeGraphDependencyPayloadKeys.BINARY_NAME to "codegraph",
      CodeGraphDependencyPayloadKeys.VERSION_ARGV to listOf("version"),
      CodeGraphDependencyPayloadKeys.INIT_ARGV to listOf("init", "--yes"),
      CodeGraphDependencyPayloadKeys.INDEX_ARGV to listOf("index"),
      CodeGraphDependencyPayloadKeys.SYNC_ARGV to listOf("sync"),
      CodeGraphDependencyPayloadKeys.QUERY_JSON_ARGV_PREFIX to listOf("query", "--json"),
      CodeGraphDependencyPayloadKeys.QUERY_OUTPUT_CONTRACT_VERSION to "0.1",
    ),
    CodeGraphDependencyPayloadKeys.TELEMETRY to mapOf(
      CodeGraphDependencyPayloadKeys.DISABLE_ENVIRONMENT to listOf("CODEGRAPH_TELEMETRY=0"),
    ),
  )
}
