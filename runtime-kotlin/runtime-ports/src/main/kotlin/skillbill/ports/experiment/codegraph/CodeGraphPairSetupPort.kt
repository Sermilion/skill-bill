package skillbill.ports.experiment.codegraph
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupRequest
import skillbill.ports.experiment.codegraph.model.CodeGraphPairSetupResult

object CodeGraphConfigurationKeys {
  const val EXECUTABLE_OVERRIDE_ENV: String = "SKILL_BILL_CODEGRAPH_EXECUTABLE"
}

interface CodeGraphPairSetupPort {
  fun provisionAfterConfirmation(request: CodeGraphPairSetupRequest): CodeGraphPairSetupResult

  fun releaseOwnedResources(pairId: String)
}
