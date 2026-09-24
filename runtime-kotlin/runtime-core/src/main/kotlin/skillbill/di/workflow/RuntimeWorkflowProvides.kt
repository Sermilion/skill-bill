package skillbill.di.workflow

import me.tatarka.inject.annotations.Provides
import skillbill.application.decomposition.DecompositionManifestWriter
import skillbill.infrastructure.workflow.decomposition.FileSystemDecompositionManifestFileStore
import skillbill.ports.decomposition.DecompositionManifestProjectionWriter
import skillbill.ports.workflow.decomposition.DecompositionManifestStore

internal interface RuntimeWorkflowProvides {
  @Provides
  fun decompositionManifestStore(): DecompositionManifestStore = FileSystemDecompositionManifestFileStore()

  @Provides
  fun decompositionManifestProjectionWriter(
    writer: DecompositionManifestWriter,
  ): DecompositionManifestProjectionWriter = writer
}
