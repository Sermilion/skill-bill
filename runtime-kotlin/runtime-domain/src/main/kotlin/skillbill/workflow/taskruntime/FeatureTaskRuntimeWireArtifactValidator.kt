package skillbill.workflow.taskruntime

interface FeatureTaskRuntimeWireArtifactValidator {
  fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String)
}
