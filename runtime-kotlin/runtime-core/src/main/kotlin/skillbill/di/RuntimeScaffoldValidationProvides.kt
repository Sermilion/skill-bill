package skillbill.di

import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.host.FileSystemRepoLocalConfig
import skillbill.infrastructure.skills.FileSystemRepoValidationGateway
import skillbill.infrastructure.skills.scaffold.adapters.FileSystemScaffoldRepoValidation
import skillbill.infrastructure.workflow.validation.FileSystemValidationGateRunner
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.scaffold.repo.ScaffoldRepoValidationPort
import skillbill.ports.validation.RepoValidationGateway
import skillbill.ports.validation.ValidationGateRunner

internal interface RuntimeScaffoldValidationProvides {
  @Provides @JvmSynthetic
  fun scaffoldRepoValidationPort(adapter: FileSystemScaffoldRepoValidation): ScaffoldRepoValidationPort = adapter

  @Provides @JvmSynthetic
  fun repoLocalConfigPort(adapter: FileSystemRepoLocalConfig): RepoLocalConfigPort = adapter

  @Provides @JvmSynthetic
  fun repoValidationGateway(gateway: FileSystemRepoValidationGateway): RepoValidationGateway = gateway

  @Provides @JvmSynthetic
  fun validationGateRunner(runner: FileSystemValidationGateRunner): ValidationGateRunner = runner
}
