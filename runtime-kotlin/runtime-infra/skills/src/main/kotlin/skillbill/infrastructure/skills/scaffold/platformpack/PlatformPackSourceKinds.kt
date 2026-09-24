package skillbill.infrastructure.skills.scaffold.platformpack

import skillbill.scaffold.policy.platformpack.model.PlatformPackSourceKind
import java.nio.file.Path

internal fun sourceKind(
  platformPacksRoot: Path,
  source: Path,
): PlatformPackSourceKind =
  if (
    source.toAbsolutePath().normalize().startsWith(platformPacksRoot.toAbsolutePath().normalize())
  ) {
    PlatformPackSourceKind.BUNDLED
  } else {
    PlatformPackSourceKind.EXTERNAL
  }
