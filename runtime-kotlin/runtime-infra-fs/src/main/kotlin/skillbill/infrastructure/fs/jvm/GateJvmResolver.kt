package skillbill.infrastructure.fs.jvm

import me.tatarka.inject.annotations.Inject
import skillbill.ports.diagnostics.RuntimeDiagnostics
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

sealed interface GateJvmDisposition {
  data class Export(val javaHome: String) : GateJvmDisposition

  object LeaveUnset : GateJvmDisposition

  data class Unresolved(val rejectedCandidate: String, val requiredMajor: String) : GateJvmDisposition
}

fun GateJvmDisposition.applyTo(environment: MutableMap<String, String>) {
  when (this) {
    is GateJvmDisposition.Export -> environment[GateJvmEnvironmentKeys.JAVA_HOME] = javaHome
    GateJvmDisposition.LeaveUnset -> environment.remove(GateJvmEnvironmentKeys.JAVA_HOME)
    is GateJvmDisposition.Unresolved -> environment.remove(GateJvmEnvironmentKeys.JAVA_HOME)
  }
}

@Inject
class GateJvmResolver(
  private val diagnostics: RuntimeDiagnostics,
) {
  fun resolve(childEnvironment: Map<String, String>): GateJvmDisposition {
    val imageRoot = runtimeImageRoot()
    val sanitized = withoutRuntimeImageJavaHomes(childEnvironment, imageRoot)
    val memoKey = GateJvmMemoKey(
      skillBillJavaHome = sanitized[GateJvmEnvironmentKeys.SKILL_BILL_JAVA_HOME],
      javaHome = sanitized[GateJvmEnvironmentKeys.JAVA_HOME],
      path = sanitized[GateJvmEnvironmentKeys.PATH],
    )
    val disposition = memoizedDispositions.computeIfAbsent(memoKey) {
      evaluateGuard(sanitized, rejectedCandidate(childEnvironment))
    }
    recordDecision(childEnvironment, sanitized, imageRoot, disposition)
    return disposition
  }

  private fun evaluateGuard(environment: Map<String, String>, rejectedCandidate: String): GateJvmDisposition {
    val guard = materializeGuard()
    return try {
      dispositionOf(runGuard(guard, environment), rejectedCandidate)
    } finally {
      runCatching { Files.deleteIfExists(guard) }
    }
  }

  private fun materializeGuard(): Path {
    val bytes = GateJvmResolver::class.java.classLoader
      .getResourceAsStream(GUARD_CLASSPATH_RESOURCE)
      ?.use { stream -> stream.readBytes() }
      ?: throw GateJvmGuardResourceMissingException(GUARD_CLASSPATH_RESOURCE)
    val guard = Files.createTempFile("skill-bill-java-guard", ".sh", OWNER_ONLY)
    Files.write(guard, bytes)
    return guard
  }

  private fun runGuard(guard: Path, environment: Map<String, String>): GuardEvaluation {
    val builder = ProcessBuilder("sh", "-c", GUARD_PROGRAM, "sh", guard.toString())
      .redirectError(ProcessBuilder.Redirect.DISCARD)
    builder.environment().clear()
    builder.environment().putAll(environment)
    val process = try {
      builder.start()
    } catch (error: IOException) {
      throw GateJvmGuardExecutionException("no POSIX sh available to evaluate $guard", error)
    }
    process.outputStream.close()
    if (!process.waitFor(GUARD_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      process.destroyForcibly()
      throw GateJvmGuardTimeoutException(GUARD_TIMEOUT_SECONDS)
    }
    val stdout = process.inputStream.use { stream -> stream.readBytes().toString(Charsets.UTF_8) }
    return GuardEvaluation(status = process.exitValue(), stdout = stdout)
  }

  private fun dispositionOf(evaluation: GuardEvaluation, rejectedCandidate: String): GateJvmDisposition {
    val output = parseGuardOutput(evaluation)
    return when (evaluation.status) {
      GUARD_RESOLVED_EXIT ->
        if (output.resolvedHome.isEmpty()) {
          GateJvmDisposition.LeaveUnset
        } else {
          GateJvmDisposition.Export(output.resolvedHome)
        }

      GUARD_REMEDIATION_EXIT -> GateJvmDisposition.Unresolved(rejectedCandidate, output.requiredMajor)
      else -> throw GateJvmGuardExecutionException("guard exited with unexpected status ${evaluation.status}")
    }
  }

  private fun parseGuardOutput(evaluation: GuardEvaluation): GuardOutput {
    val lines = evaluation.stdout.split("\n")
    val requiredMajor = lines.getOrNull(1)?.trim().orEmpty()
    if (lines.size < GUARD_OUTPUT_LINES || requiredMajor.isEmpty()) {
      throw GateJvmGuardOutputException("exit=${evaluation.status} output=${evaluation.stdout.trim()}")
    }
    return GuardOutput(resolvedHome = lines[0].trim(), requiredMajor = requiredMajor)
  }

  private fun recordDecision(
    childEnvironment: Map<String, String>,
    sanitized: Map<String, String>,
    imageRoot: Path?,
    disposition: GateJvmDisposition,
  ) {
    val dropped = GateJvmEnvironmentKeys.JAVA_HOME_CANDIDATES
      .filter { key -> childEnvironment[key] != null && sanitized[key] == null }
      .joinToString(",") { key -> "$key=${childEnvironment[key]}" }
      .ifEmpty { "none" }
    val imageRootLabel = imageRoot?.toString() ?: "unknown"
    diagnostics.warning(
      "Gate JVM resolution: seam=GateJvmResolver.resolve branch=${branchOf(sanitized, disposition)} " +
        "used=${usedValueOf(disposition)} dropped_image_candidates=$dropped image_root=$imageRootLabel",
    )
  }

  private companion object {
    const val GUARD_CLASSPATH_RESOURCE = "skillbill/infrastructure/fs/jvm/skill-bill-java-guard.sh"
    const val GUARD_TIMEOUT_SECONDS = 60L
    const val GUARD_RESOLVED_EXIT = 0
    const val GUARD_REMEDIATION_EXIT = 1
    const val GUARD_OUTPUT_LINES = 2

    val OWNER_ONLY = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))

    val memoizedDispositions = ConcurrentHashMap<GateJvmMemoKey, GateJvmDisposition>()

    val GUARD_PROGRAM = """
      trap 'skill_bill_gate_exit=${'$'}?
      printf "%s\n%s\n" "${'$'}{JAVA_HOME:-}" "${'$'}{skill_bill_required_java_major:-}"
      exit ${'$'}skill_bill_gate_exit' EXIT
      . "${'$'}1" >/dev/null
    """.trimIndent()

    fun rejectedCandidate(childEnvironment: Map<String, String>): String =
      GateJvmEnvironmentKeys.JAVA_HOME_CANDIDATES
        .firstNotNullOfOrNull { key -> childEnvironment[key]?.takeIf(String::isNotBlank) }
        ?: "<unset>"

    fun branchOf(sanitized: Map<String, String>, disposition: GateJvmDisposition): String = when (disposition) {
      is GateJvmDisposition.Export -> when (disposition.javaHome) {
        sanitized[GateJvmEnvironmentKeys.SKILL_BILL_JAVA_HOME] -> "skill_bill_java_home"
        sanitized[GateJvmEnvironmentKeys.JAVA_HOME] -> "inherited_java_home"
        else -> "scan"
      }

      GateJvmDisposition.LeaveUnset -> "path_java"
      is GateJvmDisposition.Unresolved -> "unresolved"
    }

    fun usedValueOf(disposition: GateJvmDisposition): String = when (disposition) {
      is GateJvmDisposition.Export -> disposition.javaHome
      GateJvmDisposition.LeaveUnset -> "<unset>"
      is GateJvmDisposition.Unresolved -> "<none>"
    }
  }
}

internal data class GateJvmMemoKey(
  val skillBillJavaHome: String?,
  val javaHome: String?,
  val path: String?,
)

private data class GuardEvaluation(val status: Int, val stdout: String)

private data class GuardOutput(val resolvedHome: String, val requiredMajor: String)

internal fun runtimeImageRoot(): Path? = runCatching {
  Path.of(System.getProperty("java.home").orEmpty()).toRealPath()
}.getOrNull()

internal fun withoutRuntimeImageJavaHomes(
  environment: Map<String, String>,
  imageRoot: Path?,
): Map<String, String> = environment.filterNot { (key, value) ->
  key in GateJvmEnvironmentKeys.JAVA_HOME_CANDIDATES && liesInside(value, imageRoot)
}

private fun liesInside(value: String, imageRoot: Path?): Boolean {
  if (imageRoot == null || value.isBlank()) return false
  val candidate = runCatching { Path.of(value).toRealPath() }.getOrNull() ?: return false
  return candidate.startsWith(imageRoot)
}
