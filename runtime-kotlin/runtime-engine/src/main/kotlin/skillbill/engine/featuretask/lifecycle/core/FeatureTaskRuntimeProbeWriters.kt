package skillbill.engine.featuretask.lifecycle.core

import me.tatarka.inject.annotations.Inject
import skillbill.application.idestatus.AgentActivityStampWriter
import skillbill.engine.worktreeedit.WorktreeEditJournalWriter

@Inject
class FeatureTaskRuntimeProbeWriters(
  val activityStampWriter: AgentActivityStampWriter,
  val worktreeEditJournalWriter: WorktreeEditJournalWriter,
)
