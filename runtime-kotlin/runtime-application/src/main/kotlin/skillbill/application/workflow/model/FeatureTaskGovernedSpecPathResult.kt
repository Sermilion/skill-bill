package skillbill.application.workflow.model

import java.nio.file.Path

sealed interface FeatureTaskGovernedSpecPathResult {
  data class Ok(val relativePath: String) : FeatureTaskGovernedSpecPathResult

  data class OutsideRepository(val repositoryRoot: Path) : FeatureTaskGovernedSpecPathResult

  data object InvalidGovernedPath : FeatureTaskGovernedSpecPathResult
}
