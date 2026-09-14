package skillbill.cli.skillremove

import skillbill.application.scaffold.SkillRemove
import skillbill.cli.model.CliFormat
import skillbill.cli.model.CliRunInputs

internal data class RemoveCommandExecutionRequest(
  val inputs: CliRunInputs,
  val skillRemove: SkillRemove,
  val rawTarget: String?,
  val repoRoot: String,
  val dryRun: Boolean,
  val allowShipped: Boolean,
  val format: CliFormat,
)
