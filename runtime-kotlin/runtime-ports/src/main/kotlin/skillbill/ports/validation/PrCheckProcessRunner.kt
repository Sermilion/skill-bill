package skillbill.ports.validation

import skillbill.ports.validation.model.PrCheckRunResult
import java.nio.file.Path

interface PrCheckProcessRunner {
  fun run(command: String, repoRoot: Path): PrCheckRunResult
}
