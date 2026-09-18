package skillbill.infrastructure.fs.jvm

internal object GateJvmEnvironmentKeys {
  const val JAVA_HOME = "JAVA_HOME"
  const val SKILL_BILL_JAVA_HOME = "SKILL_BILL_JAVA_HOME"
  const val PATH = "PATH"

  val JAVA_HOME_CANDIDATES = listOf(SKILL_BILL_JAVA_HOME, JAVA_HOME)
}
