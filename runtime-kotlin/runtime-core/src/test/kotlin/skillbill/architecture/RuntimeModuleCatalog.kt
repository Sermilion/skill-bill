package skillbill.architecture

object RuntimeModuleCatalog {
  data class ModuleEdgeExpectation(
    val api: Set<String>,
    val implementation: Set<String>,
  )

  val declaredGradleModules: List<String> =
    listOf(
      "runtime-application",
      "runtime-contracts",
      "runtime-core",
      "runtime-domain",
      "runtime-engine",
      "runtime-infra-fs",
      "runtime-infra-http",
      "runtime-infra-sqlite",
      "runtime-cli",
      "runtime-mcp",
      "runtime-ports",
    )

  val moduleEdgeExpectations: Map<String, ModuleEdgeExpectation> =
    mapOf(
      "runtime-application" to ModuleEdgeExpectation(
        api = setOf("runtime-contracts", "runtime-domain", "runtime-ports"),
        implementation = emptySet(),
      ),
      "runtime-contracts" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = emptySet(),
      ),
      "runtime-core" to ModuleEdgeExpectation(
        api = setOf("runtime-application", "runtime-engine", "runtime-ports"),
        implementation = setOf(
          "runtime-domain",
          "runtime-contracts",
          "runtime-infra-fs",
          "runtime-infra-http",
          "runtime-infra-sqlite",
        ),
      ),
      "runtime-engine" to ModuleEdgeExpectation(
        api = setOf("runtime-application", "runtime-contracts", "runtime-domain", "runtime-ports"),
        implementation = emptySet(),
      ),
      "runtime-domain" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = setOf("runtime-contracts"),
      ),
      "runtime-infra-fs" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = setOf("runtime-contracts", "runtime-domain", "runtime-ports"),
      ),
      "runtime-infra-http" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = setOf("runtime-contracts", "runtime-domain", "runtime-ports"),
      ),
      "runtime-infra-sqlite" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = setOf("runtime-contracts", "runtime-domain", "runtime-ports"),
      ),
      "runtime-cli" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = setOf(
          "runtime-application",
          "runtime-contracts",
          "runtime-core",
          "runtime-domain",
          "runtime-engine",
          "runtime-ports",
        ),
      ),
      "runtime-mcp" to ModuleEdgeExpectation(
        api = emptySet(),
        implementation = setOf(
          "runtime-application",
          "runtime-contracts",
          "runtime-core",
          "runtime-domain",
          "runtime-engine",
          "runtime-ports",
        ),
      ),
      "runtime-ports" to ModuleEdgeExpectation(
        api = setOf("runtime-contracts", "runtime-domain"),
        implementation = emptySet(),
      ),
    )

  val mainProjectDependenciesByModule: Map<String, Set<String>> =
    moduleEdgeExpectations.mapValues { (_, expectation) -> expectation.api + expectation.implementation }

  val testFixturesProjectDependenciesByModule: Map<String, Set<String>> =
    mapOf(
      "runtime-application" to setOf("runtime-domain", "runtime-infra-fs", "runtime-infra-sqlite", "runtime-ports"),
      "runtime-contracts" to emptySet(),
      "runtime-core" to emptySet(),
      "runtime-engine" to setOf("runtime-application", "runtime-domain", "runtime-infra-sqlite", "runtime-ports"),
      "runtime-domain" to emptySet(),
      "runtime-infra-fs" to emptySet(),
      "runtime-infra-http" to emptySet(),
      "runtime-infra-sqlite" to emptySet(),
      "runtime-cli" to emptySet(),
      "runtime-mcp" to emptySet(),
      "runtime-ports" to emptySet(),
    )

  val declaredSubsystemPackages: List<String> =
    listOf(
      "skillbill.agent.model",
      "skillbill.agentaddon",
      "skillbill.application",
      "skillbill.cli",
      "skillbill.config",
      "skillbill.contracts",
      "skillbill.di",
      "skillbill.domain.skillremove",
      "skillbill.engine",
      "skillbill.error",
      "skillbill.featurespec",
      "skillbill.goalrunner",
      "skillbill.idestatus",
      "skillbill.infrastructure",
      "skillbill.install",
      "skillbill.learnings",
      "skillbill.mcp",
      "skillbill.model",
      "skillbill.ports",
      "skillbill.review",
      "skillbill.scaffold",
      "skillbill.telemetry",
      "skillbill.text",
      "skillbill.workflow",
      "skillbill.workflow.verify",
    )

  val moduleMainPackageRoots: Map<String, String> =
    mapOf(
      "runtime-application" to "skillbill.application",
      "runtime-cli" to "skillbill.cli",
      "runtime-core" to "skillbill.di",
      "runtime-engine" to "skillbill.engine",
      "runtime-infra-fs" to "skillbill.infrastructure.fs",
      "runtime-infra-http" to "skillbill.infrastructure.http",
      "runtime-infra-sqlite" to "skillbill.infrastructure.sqlite",
      "runtime-mcp" to "skillbill.mcp",
    )
}
