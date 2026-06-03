package skillbill.ports.taskruntime

import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeRunInvariants
import java.nio.file.Path

/**
 * SKILL-65 Subtask 4 (AC1, AC2): the read seam through which the CLI sources the
 * feature-task-runtime run-invariants from a governed spec.
 *
 * The CLI must NOT perform raw filesystem reads (the architecture test forbids
 * direct file IO in the CLI module); it delegates the spec read + parse to
 * this injected port. The concrete adapter reads the spec at [specPath] and
 * extracts the run-invariants — the spec reference, the ordered acceptance
 * criteria, and any mandates/overrides — returning the typed domain
 * [FeatureTaskRuntimeRunInvariants] (which itself loud-fails construction when
 * required invariants are missing). The [Path] is an inert value type; the port
 * owns all filesystem access.
 */
interface FeatureTaskRuntimeRunInvariantsSource {
  fun read(specPath: Path): FeatureTaskRuntimeRunInvariants
}
