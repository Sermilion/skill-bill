package skillbill.workflow.taskruntime

object NoopFeatureTaskRuntimeWireArtifactValidator : FeatureTaskRuntimeWireArtifactValidator {
  override fun validate(kind: FeatureTaskRuntimeWireArtifactKind, payload: Any, sourceLabel: String) {
  }
}
