package skillbill.cli

import skillbill.telemetry.HttpRequester
import skillbill.telemetry.TelemetryHttpRuntime
import java.nio.file.Path

data class CliExecutionResult(
  val exitCode: Int,
  val stdout: String,
  val payload: Map<String, Any?>? = null,
)

data class CliRuntimeContext(
  val dbPathOverride: String? = null,
  val stdinText: String? = null,
  val environment: Map<String, String> = System.getenv(),
  val userHome: Path = Path.of(System.getProperty("user.home")),
  val requester: HttpRequester = TelemetryHttpRuntime.defaultHttpRequester,
)

internal class ArgumentCursor(arguments: List<String>) {
  private val args = arguments.toList()
  private var index: Int = 0

  fun hasNext(): Boolean = index < args.size

  fun peek(): String {
    require(hasNext()) { "Missing command arguments." }
    return args[index]
  }

  fun take(): String {
    require(hasNext()) { "Missing command arguments." }
    return args[index++]
  }

  fun requireValue(flag: String): String {
    require(hasNext()) { "$flag requires a value." }
    return take()
  }

  fun rejectExtraArguments(commandName: String) {
    require(!hasNext()) {
      "Unexpected arguments for $commandName: ${args.subList(index, args.size).joinToString(" ")}"
    }
  }
}
