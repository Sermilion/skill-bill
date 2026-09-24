package skillbill.scaffold.policy.platformpack

import skillbill.model.FileLocation
import skillbill.scaffold.policy.sharedContractNote

fun platformPackNotes(
  platform: String,
  presetUsed: Boolean,
  selectedAreas: List<String>,
): List<String> {
  val notes = mutableListOf<String>()
  if (presetUsed) {
    notes +=
      "Applied built-in platform preset for '$platform'. " +
      "Override 'routing_signals' only when the defaults need adjustment."
  }
  notes += "Full platform pack scaffolded with ${selectedAreas.size} approved code-review area stubs."
  notes += "Edit the generated pack content or remove unused stubs instead of creating partial follow-on scaffolds."
  notes += sharedContractNote()
  return notes
}

fun buildPlatformPackInstallPaths(
  packRoot: FileLocation,
  baselineName: String,
  specialistPaths: Map<String, FileLocation>,
  selectedAreas: List<String>,
): List<FileLocation> =
  buildList {
    add(packRoot.resolve("code-review").resolve(baselineName))
    selectedAreas.forEach { area ->
      add(specialistPaths.getValue(area))
    }
  }
