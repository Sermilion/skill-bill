package skillbill.cli.core

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parsers.CommandLineParser
import skillbill.cli.kernel.cli.CliRunState
import skillbill.cli.model.CliExecutionResult
import skillbill.cli.model.CliRunInputs
import skillbill.cli.model.CliRuntimeContext
import skillbill.cli.model.CliStdoutCompletion
import skillbill.di.core.RuntimeComponent
import skillbill.di.core.create
import skillbill.error.core.SkillBillRuntimeException
import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CancellationException

object CliRuntime {
  fun run(
    arguments: List<String>,
    context: CliRuntimeContext = CliRuntimeContext(),
  ): CliExecutionResult {
    val rootFlags = RootFlagProbeCommand()
    runCatching { CommandLineParser.parseAndRun(rootFlags, arguments) { } }
    val runtimeComponent =
      RuntimeComponent::class.create(
        context.toRuntimeContext(
          dbPathOverride = rootFlags.dbOverride ?: context.dbPathOverride,
          userHome = rootFlags.homeOverride?.let(Path::of) ?: context.userHome,
        ),
      )
    val resolved = runtimeComponent.resolvedEnvironmentContext
    val runState = CliRunState(context.stdinText)
    val runInputs =
      CliRunInputs(
        databasePath = resolved.dbPathOverride,
        environment = resolved.environment,
        userHome = resolved.userHome,
        repositoryRoot = resolved.repositoryRoot,
        repositoryEnclosingRootPort = runtimeComponent.repositoryEnclosingRootPort,
        liveStdout = context.liveStdout,
        liveStderr = context.liveStderr,
      )
    val cliComponent = CliComponent::class.create(runtimeComponent, runState, runInputs)
    val rootCommand = cliComponent.rootCommand
    return try {
      CommandLineParser.parseAndRun(rootCommand, arguments) { command -> command.run() }
      cliComponent.runState.result
        ?: CliExecutionResult(
          exitCode = 0,
          stdout = rootCommand.getFormattedHelp().orEmpty(),
          stdoutCompletion = CliStdoutCompletion.IMPLICIT,
        )
    } catch (error: CliktError) {
      val usage = rootCommand.getFormattedHelp(error).orEmpty()
      if (error.statusCode == 0) {
        CliExecutionResult(
          exitCode = 0,
          stdout = usage,
          stderr = runState.currentStderr(),
        )
      } else {
        CliExecutionResult(
          exitCode = error.statusCode,
          stdout = "",
          stderr =
            diagnosticWithPrefix(
              runState.currentStderr(),
              usage.ifBlank { oneLine(error.message, "Command failed.") },
            ),
        )
      }
    } catch (error: IllegalArgumentException) {
      CliExecutionResult(
        exitCode = 1,
        stdout = "",
        stderr = diagnosticWithPrefix(runState.currentStderr(), oneLine(error.message, "Invalid command argument.")),
      )
    } catch (error: SkillBillRuntimeException) {
      CliExecutionResult(
        exitCode = 1,
        stdout = "",
        stderr = diagnosticWithPrefix(runState.currentStderr(), oneLine(error.message, "Command failed.")),
      )
    } catch (error: NoSuchFileException) {
      CliExecutionResult(
        exitCode = 1,
        stdout = "",
        stderr = diagnosticWithPrefix(runState.currentStderr(), oneLine(error.message, error.toString())),
      )
    } catch (error: AccessDeniedException) {
      CliExecutionResult(
        exitCode = 1,
        stdout = "",
        stderr = diagnosticWithPrefix(runState.currentStderr(), oneLine(error.message, error.toString())),
      )
    } catch (error: CancellationException) {
      throw error
    } catch (error: InterruptedException) {
      throw error
    } catch (error: Throwable) {
      val diagnostic = oneLine("${error::class.simpleName}: ${error.message}", error::class.simpleName ?: "Command failed.")
      runtimeComponent.runtimeDiagnostics.error(diagnostic, error)
      CliExecutionResult(
        exitCode = 1,
        stdout = "",
        stderr = diagnosticWithPrefix(runState.currentStderr(), diagnostic),
      )
    }
  }
}

private fun oneLine(message: String?, fallback: String): String =
  message
    ?.replace(Regex("\\s+"), " ")
    ?.trim()
    ?.ifBlank { fallback }
    ?: fallback

private fun diagnosticWithPrefix(prefix: String, diagnostic: String): String =
  if (prefix.isBlank()) diagnostic else prefix + diagnostic

private class RootFlagProbeCommand : CliktCommand("skill-bill") {
  val dbOverride by databasePathOption()
  val homeOverride by userHomeOverrideOption()
  val ignoredTokens by argument().multiple()

  override val treatUnknownOptionsAsArgs: Boolean = true

  init {
    context {
      helpOptionNames = emptySet()
      allowInterspersedArgs = false
    }
  }

  override fun run() = Unit
}
