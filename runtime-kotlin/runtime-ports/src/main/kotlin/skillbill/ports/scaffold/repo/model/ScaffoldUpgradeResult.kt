package skillbill.ports.scaffold.repo.model

data class ScaffoldUpgradeResult(
  val repoRoot: String,
  val regeneratedCount: Int,
  val regeneratedFiles: List<String>,
  val contentMdTouched: Boolean,
  val shellCeremonyTouched: Boolean,
  val validatorRan: Boolean,
)
