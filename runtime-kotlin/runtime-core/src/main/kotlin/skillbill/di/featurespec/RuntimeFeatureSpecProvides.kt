package skillbill.di.featurespec

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.workflow.filesystem.FileSystemFeatureSpecPathResolver
import skillbill.infrastructure.workflow.filesystem.FileSystemSpecScratchStore
import skillbill.ports.featurespec.FeatureSpecPathResolverPort
import skillbill.ports.workflow.specscratch.SpecScratchStore

internal interface RuntimeFeatureSpecProvides {
  @Provides
  fun specScratchStore(store: FileSystemSpecScratchStore): SpecScratchStore = store

  @Provides
  fun featureSpecPathResolverPort(adapter: FileSystemFeatureSpecPathResolver): FeatureSpecPathResolverPort = adapter
}
