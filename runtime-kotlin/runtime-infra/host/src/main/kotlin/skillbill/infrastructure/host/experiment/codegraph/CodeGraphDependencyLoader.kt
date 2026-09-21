package skillbill.infrastructure.host.experiment.codegraph

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.experiment.codegraph.CODEGRAPH_DEPENDENCY_CONTRACT_VERSION
import skillbill.contracts.experiment.codegraph.CodeGraphDependencyPayloadKeys
import skillbill.error.shellcontent.MissingManifestError
import skillbill.infrastructure.contracts.experiment.codegraph.CodeGraphDependencySchemaValidator
import skillbill.infrastructure.host.experiment.catalog.FileSystemExperimentDescriptorCatalog
import java.nio.file.Files
import java.nio.file.Path

object CodeGraphDependencyLoader {
  private val yamlMapper: YAMLMapper = YAMLMapper()

  fun load(startPath: Path): Map<String, Any?> {
    val repoRoot = FileSystemExperimentDescriptorCatalog.findRepoRootForExperiments(startPath)
    val fromRepo = repoRoot?.resolve(CodeGraphDependencyPaths.RELATIVE_FILE)?.takeIf { Files.isRegularFile(it) }
    val rawText = when {
      fromRepo != null -> Files.readString(fromRepo)
      else -> {
        val stream = CodeGraphDependencyLoader::class.java.classLoader
          .getResourceAsStream(CodeGraphDependencyPaths.CLASSPATH_RESOURCE)
          ?: throw MissingManifestError(
            "CodeGraph dependency declaration is missing at '${CodeGraphDependencyPaths.RELATIVE_FILE}' " +
              "and '${CodeGraphDependencyPaths.CLASSPATH_RESOURCE}' is not on the classpath.",
          )
        stream.bufferedReader().readText()
      }
    }
    val payload = (yamlMapper.readValue(rawText, Map::class.java) as Map<*, *>)
      .entries
      .associate { entry -> entry.key.toString() to entry.value }
    val label = fromRepo?.toString() ?: CodeGraphDependencyPaths.CLASSPATH_RESOURCE
    CodeGraphDependencySchemaValidator.validate(payload, label)
    CodeGraphDependencySchemaValidator.validateDownloadPolicy(payload, label)
    val version = payload[CodeGraphDependencyPayloadKeys.CONTRACT_VERSION]?.toString()?.trim()
    if (version != CODEGRAPH_DEPENDENCY_CONTRACT_VERSION) {
      throw MissingManifestError(
        "CodeGraph dependency contract_version '$version' does not match $CODEGRAPH_DEPENDENCY_CONTRACT_VERSION.",
      )
    }
    return payload
  }
}
