package skillbill.infrastructure.skills.scaffold.runtime.service
import skillbill.infrastructure.skills.scaffold.runtime.service.standalone.scaffold
import java.nio.file.Path

internal fun performInstall(
  txn: ScaffoldTransaction,
  plan: ScaffoldPlan,
  repoRoot: Path,
  adapters: ScaffoldAdapterSeams,
): Pair<List<Path>, List<String>> = adapters.performInstall(txn, plan, repoRoot)
