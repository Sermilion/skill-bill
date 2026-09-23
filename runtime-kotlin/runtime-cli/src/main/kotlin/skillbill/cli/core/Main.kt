package skillbill.cli.core

import skillbill.cli.model.CliRuntimeContext
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val result =
    CliRuntime.run(
      args.toList(),
      CliRuntimeContext(
        liveStdout = { print(it) },
        liveStderr = { System.err.print(it) },
      ),
    )
  emitCliProcessStdout(result, System.out)
  if (result.stderr.isNotEmpty()) {
    System.err.print(result.stderr)
  }
  exitProcess(result.exitCode)
}
