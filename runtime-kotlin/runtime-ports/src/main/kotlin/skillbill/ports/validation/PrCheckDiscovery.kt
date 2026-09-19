package skillbill.ports.validation

import skillbill.ports.validation.model.PrCheckDiscoveryResult
import java.nio.file.Path

interface PrCheckDiscovery {
  fun discoverPullRequestChecks(repoRoot: Path): PrCheckDiscoveryResult
}
