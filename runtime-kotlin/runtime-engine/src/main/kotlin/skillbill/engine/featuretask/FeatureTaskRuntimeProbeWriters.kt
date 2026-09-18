package skillbill.engine.featuretask

import me.tatarka.inject.annotations.Inject
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.application.idestatus.WorktreeEditJournalWriter

@Inject
class FeatureTaskRuntimeProbeWriters(
  val activityStampWriter: AgentActivityStampWriter,
  val worktreeEditJournalWriter: WorktreeEditJournalWriter,
)
