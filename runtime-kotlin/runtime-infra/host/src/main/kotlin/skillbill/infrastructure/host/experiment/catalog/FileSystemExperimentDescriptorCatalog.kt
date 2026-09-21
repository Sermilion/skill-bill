package skillbill.infrastructure.host.experiment.catalog
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.experiment.ExperimentDescriptorPayloadKeys
import skillbill.error.shellcontent.InvalidExperimentDescriptorSchemaError
import skillbill.error.shellcontent.MissingManifestError
import skillbill.experiment.model.ExperimentExecutionMode
import skillbill.infrastructure.contracts.experiment.ExperimentDescriptorSchemaValidator
import skillbill.ports.experiment.descriptor.ExperimentDescriptorCatalog
import skillbill.ports.experiment.descriptor.model.ExperimentDescriptorRecord
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.useDirectoryEntries

object FileSystemExperimentDescriptorCatalog {
  private val yamlMapper: YAMLMapper = YAMLMapper()

  fun discover(startPath: Path): ExperimentDescriptorCatalog {
    val repoRoot = findRepoRootForExperiments(startPath)
      ?: throw MissingManifestError(
        "Experiment descriptor catalog is unavailable: no '${ExperimentCatalogPaths.RELATIVE_DIR}' directory " +
          "was found walking up from '$startPath'.",
      )
    val records = loadDescriptors(repoRoot)
    return catalog(records)
  }

  fun hasCatalog(startPath: Path): Boolean = findRepoRootForExperiments(startPath) != null

  fun catalog(records: List<ExperimentDescriptorRecord>): ExperimentDescriptorCatalog =
    object : ExperimentDescriptorCatalog {
      override fun listCompatible(mode: ExperimentExecutionMode): List<ExperimentDescriptorRecord> =
        records.filter { it.executionMode == mode }

      override fun resolve(name: String, mode: ExperimentExecutionMode): ExperimentDescriptorRecord? =
        records.firstOrNull { it.name == name && it.executionMode == mode }
    }

  internal fun findRepoRootForExperiments(start: Path): Path? {
    var current: Path? = start.toAbsolutePath().normalize().let { if (Files.isDirectory(it)) it else it.parent }
    while (current != null) {
      if (Files.isDirectory(current.resolve(ExperimentCatalogPaths.RELATIVE_DIR))) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun loadDescriptors(repoRoot: Path): List<ExperimentDescriptorRecord> {
    val catalogDir = repoRoot.resolve(ExperimentCatalogPaths.RELATIVE_DIR).toAbsolutePath().normalize()
    if (!Files.isDirectory(catalogDir)) {
      throw MissingManifestError("Experiment descriptor catalog '$catalogDir' is missing.")
    }
    val descriptorFiles = catalogDir.useDirectoryEntries("*.yaml") { stream -> stream.sorted().toList() }
    if (descriptorFiles.isEmpty()) {
      throw MissingManifestError("Experiment descriptor catalog '$catalogDir' is empty.")
    }
    return descriptorFiles.map { file -> loadDescriptor(file) }
  }

  private fun loadDescriptor(file: Path): ExperimentDescriptorRecord {
    val rawText = Files.readString(file)
    val payload = (yamlMapper.readValue(rawText, Map::class.java) as Map<*, *>)
      .entries
      .associate { entry -> entry.key.toString() to entry.value }
    val label = file.toString()
    ExperimentDescriptorSchemaValidator.validate(payload, label)
    val modeWire = payload[ExperimentDescriptorPayloadKeys.EXECUTION_MODE]?.toString()?.trim().orEmpty()
    val mode = ExperimentExecutionMode.entries.firstOrNull { it.wireValue == modeWire }
      ?: throw InvalidExperimentDescriptorSchemaError(label, "unknown execution_mode '$modeWire'.")
    val capabilities = (payload[ExperimentDescriptorPayloadKeys.REQUIRED_LAUNCHER_CAPABILITIES] as? List<*>)
      ?.filterIsInstance<String>()
      ?: emptyList()
    val setup = (payload[ExperimentDescriptorPayloadKeys.SETUP_REQUIREMENTS] as? List<*>)
      ?.filterIsInstance<String>()
      ?: emptyList()
    val measurement = (payload[ExperimentDescriptorPayloadKeys.MEASUREMENT_REQUIREMENTS] as? List<*>)
      ?.filterIsInstance<String>()
      ?: emptyList()
    return ExperimentDescriptorRecord(
      name = payload[ExperimentDescriptorPayloadKeys.NAME]?.toString()?.trim().orEmpty(),
      descriptorVersion = payload[ExperimentDescriptorPayloadKeys.DESCRIPTOR_VERSION]?.toString()?.trim().orEmpty(),
      executionMode = mode,
      requiredLauncherCapabilities = capabilities.toSet(),
      treatmentCapability = payload[ExperimentDescriptorPayloadKeys.TREATMENT_CAPABILITY]?.toString()?.trim().orEmpty(),
      setupRequirements = setup,
      measurementRequirements = measurement,
    )
  }
}
