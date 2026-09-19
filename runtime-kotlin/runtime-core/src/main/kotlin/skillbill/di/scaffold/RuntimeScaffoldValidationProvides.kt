package skillbill.di.scaffold
import me.tatarka.inject.annotations.Provides
import skillbill.infrastructure.host.FileSystemRepoLocalConfig
import skillbill.infrastructure.skills.scaffold.FileSystemRepoValidationGateway
import skillbill.infrastructure.skills.scaffold.adapters.FileSystemScaffoldRepoValidation
import skillbill.infrastructure.workflow.github.GitHubPullRequestCheckDiscovery
import skillbill.infrastructure.workflow.validation.FileSystemPrCheckProcessRunner
import skillbill.infrastructure.workflow.validation.FileSystemValidationGateRunner
import skillbill.ports.config.RepoLocalConfigPort
import skillbill.ports.scaffold.repo.ScaffoldRepoValidationPort
import skillbill.ports.validation.PrCheckDiscovery
import skillbill.ports.validation.PrCheckProcessRunner
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

  @Provides @JvmSynthetic
  fun prCheckDiscovery(discovery: GitHubPullRequestCheckDiscovery): PrCheckDiscovery = discovery

  @Provides @JvmSynthetic
  fun prCheckProcessRunner(runner: FileSystemPrCheckProcessRunner): PrCheckProcessRunner = runner
}
