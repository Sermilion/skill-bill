package skillbill.cli.goal.run
import skillbill.cli.goal.core.Map
import skillbill.cli.goal.purge.Map
import skillbill.cli.goal.status.hunks
import skillbill.cli.goal.status.lines
import skillbill.cli.goal.status.path
import skillbill.cli.goal.status.staged
import skillbill.workflow.goal.model.GoalObservabilityDiffStat
import skillbill.workflow.goal.model.GoalObservabilitySelectedDiffHunk
import skillbill.workflow.goal.model.GoalObservabilitySelectedDiffHunks

internal fun GoalObservabilityDiffStat.toGoalDiffStatCliMap(): Map<String, Any?> = linkedMapOf(
  "files_changed" to filesChanged,
  "insertions" to insertions,
  "deletions" to deletions,
)

internal fun GoalObservabilitySelectedDiffHunks.toGoalSelectedDiffHunksCliMap(): Map<String, Any?> = linkedMapOf(
  "truncated" to truncated,
  "hunks" to hunks.map(GoalObservabilitySelectedDiffHunk::toGoalSelectedDiffHunkCliMap),
)

private fun GoalObservabilitySelectedDiffHunk.toGoalSelectedDiffHunkCliMap(): Map<String, Any?> = linkedMapOf(
  "path" to path,
  "staged" to staged,
  "header" to header,
  "truncated" to truncated,
  "lines" to lines,
)
