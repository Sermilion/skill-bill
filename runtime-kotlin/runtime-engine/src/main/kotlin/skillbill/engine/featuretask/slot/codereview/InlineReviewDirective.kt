package skillbill.engine.featuretask.slot.codereview

import skillbill.engine.featuretask.slot.ReviewTarget
import java.nio.file.Path

object InlineReviewDirective {
  fun compose(
    target: ReviewTarget,
    baseRevision: String,
    headRevision: String,
    specPath: Path?,
    agentAddonsSection: String,
  ): String {
    val prompt =
      buildString {
        target.openingLines(baseRevision, headRevision).forEach(::appendLine)
        appendLine("Do not use `origin/main...HEAD`, a merge base, the full feature branch, or a pre-baked diff blob.")
        appendLine("Do not launch bill-code-review, delegated review subagents, or an isolated review process.")
        appendLine("Fix every Blocker and Major finding in this same session before you emit.")
        appendLine("You may edit files. Leave Minor and Nit unfixed unless the edit is local and obvious.")
        appendLine("Do not commit, amend, reset, or stage changes; the runtime owns the review checkpoint.")
        appendLine(
          "Criterion-gap detection remains exclusive to audit. Do not report unsatisfied acceptance criteria.",
        )
        appendLine(
          "Do not run `./gradlew check`, the pack collect-all gate, or `bill-code-check`; validate owns those.",
        )
        specPath?.let { path -> appendLine("Subtask spec path: `$path`.") }
        appendLine("After fixes, emit remaining findings in this register shape, one per line:")
        appendLine("- [F-001] Blocker | High | path/File.kt:12 | remaining defect after your edits")
        appendLine("End with exactly one line: `verdict: approved` or `verdict: changes_requested`.")
        appendLine("Use `changes_requested` when any Blocker or Major remains; otherwise `approved`.")
        appendLine("An explicit empty findings list plus `verdict: approved` means no remaining Blocker or Major.")
      }
    if (agentAddonsSection.isEmpty()) return prompt
    return prompt.trimEnd() + "\n\n" + agentAddonsSection
  }
}
