package skillbill.infrastructure.host.jvm
import skillbill.error.core.SkillBillRuntimeException
internal class GateJvmGuardResourceMissingException(resource: String) : SkillBillRuntimeException(
  "Gate JVM guard is missing from the runtime distribution at classpath resource '$resource'. " +
    "Reinstall the runtime so the Java guard ships inside the image.",
)

internal class GateJvmGuardExecutionException(detail: String, cause: Throwable? = null) : SkillBillRuntimeException(
  "Gate JVM guard could not be evaluated: $detail",
  cause,
)

internal class GateJvmGuardOutputException(detail: String) : SkillBillRuntimeException(
  "Gate JVM guard returned no usable resolution output: $detail",
)

internal class GateJvmGuardTimeoutException(timeoutSeconds: Long) : SkillBillRuntimeException(
  "Gate JVM guard evaluation timed out after ${timeoutSeconds}s.",
)

class GateJvmUnresolvedException(
  val rejectedCandidate: String,
  val requiredMajor: String,
) : SkillBillRuntimeException(
  "No Java $requiredMajor+ runtime resolved for the pack gate command; rejected candidate: $rejectedCandidate. " +
    "Set SKILL_BILL_JAVA_HOME to a Java $requiredMajor+ installation and retry.",
)

class GateJvmStartupFailureException(
  val resolvedJvm: String,
  excerpt: String?,
) : SkillBillRuntimeException(
  "The pack gate command could not start a JVM on the resolved Java home '$resolvedJvm'; " +
    "this is an environment defect, not a repairable gate finding. " +
    "Set SKILL_BILL_JAVA_HOME to a working JDK and retry." +
    (excerpt?.let { "\nGate stdout (head+tail):\n$it" } ?: ""),
)
