package skillbill.workflow.taskruntime.model.core

fun interface FeatureTaskRuntimeWireArtifactValidation {
  operator fun invoke(
    kind: FeatureTaskRuntimeWireArtifactKind,
    artifact: FeatureTaskRuntimeWorkflowArtifactMap,
    sourceLabel: String,
  )
}
