package skillbill.contracts.install

const val INSTALL_PLAN_CONTRACT_VERSION: String = "0.3"

object InstallPlanSchemaPaths {
  const val REPO_RELATIVE_PATH: String =
    "orchestration/contracts/install-plan-schema.yaml"

  const val CLASSPATH_RESOURCE: String =
    "skillbill/infrastructure/contracts/install-plan-schema.yaml"

  const val EXPECTED_SCHEMA_ID: String =
    "https://skill-bill.dev/contracts/install-plan-schema.yaml"
}
