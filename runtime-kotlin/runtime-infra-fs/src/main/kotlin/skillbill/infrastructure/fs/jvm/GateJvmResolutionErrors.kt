package skillbill.infrastructure.fs.jvm

import skillbill.error.SkillBillRuntimeException

class GateJvmGuardResourceMissingException(resource: String) : SkillBillRuntimeException(
  "Gate JVM guard is missing from the runtime distribution at classpath resource '$resource'. " +
    "Reinstall the runtime so the Java guard ships inside the image.",
)

class GateJvmGuardExecutionException(detail: String, cause: Throwable? = null) : SkillBillRuntimeException(
  "Gate JVM guard could not be evaluated: $detail",
  cause,
)

class GateJvmGuardOutputException(detail: String) : SkillBillRuntimeException(
  "Gate JVM guard returned no usable resolution output: $detail",
)

class GateJvmGuardTimeoutException(timeoutSeconds: Long) : SkillBillRuntimeException(
  "Gate JVM guard evaluation timed out after ${timeoutSeconds}s.",
)

class GateJvmUnresolvedException(
  val rejectedCandidate: String,
  val requiredMajor: String,
) : SkillBillRuntimeException(
  "No Java $requiredMajor+ runtime resolved for the pack gate command; rejected candidate: $rejectedCandidate. " +
    "Set SKILL_BILL_JAVA_HOME to a Java $requiredMajor+ installation and retry.",
)
