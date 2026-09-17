package skillbill.goalrunner.subtaskreview.model

import skillbill.goalrunner.model.UnaddressedFinding

data class RejectedVerificationFindingsResult(
  val findings: List<UnaddressedFinding>,
  val truncationRecords: List<String>,
)
