package skillbill.workflow.taskruntime.model.core

interface FeatureTaskRuntimeWireArtifactValidator {
  fun validate(
    kind: FeatureTaskRuntimeWireArtifactKind,
    payload: Any,
    sourceLabel: String,
  )
}
