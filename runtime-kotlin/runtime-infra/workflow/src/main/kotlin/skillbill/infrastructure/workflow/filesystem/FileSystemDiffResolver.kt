package skillbill.infrastructure.workflow.filesystem
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.host.process.BoundedExternalProcessRequest
import skillbill.infrastructure.host.process.BoundedExternalProcessRunner
import skillbill.infrastructure.workflow.decomposition.name
import skillbill.infrastructure.workflow.decomposition.root
import skillbill.infrastructure.workflow.feature.error
import skillbill.infrastructure.workflow.featuretask.error
import skillbill.infrastructure.workflow.git.goal.error
import skillbill.infrastructure.workflow.git.goal.exitCode
import skillbill.infrastructure.workflow.git.goal.output
import skillbill.infrastructure.workflow.git.goal.root
import skillbill.infrastructure.workflow.git.protected.output
import skillbill.infrastructure.workflow.git.repository.exitCode
import skillbill.infrastructure.workflow.git.repository.output
import skillbill.infrastructure.workflow.git.scoped.paths
import skillbill.infrastructure.workflow.git.standard.args
import skillbill.infrastructure.workflow.git.standard.path
import skillbill.infrastructure.workflow.git.standard.paths
import skillbill.infrastructure.workflow.git.suppression.output
import skillbill.infrastructure.workflow.git.suppression.path
import skillbill.infrastructure.workflow.git.workflow.error
import skillbill.infrastructure.workflow.git.workflow.exitCode
import skillbill.infrastructure.workflow.git.workflow.output
import skillbill.infrastructure.workflow.git.workflow.path
import skillbill.infrastructure.workflow.git.workflow.paths
import skillbill.infrastructure.workflow.git.workflow.root
import skillbill.infrastructure.workflow.review.broker.error
import skillbill.infrastructure.workflow.review.broker.path
import skillbill.infrastructure.workflow.review.broker.root
import skillbill.infrastructure.workflow.review.specialists.checkpoint.checkpointFileIdentity
import skillbill.infrastructure.workflow.review.specialists.checkpoint.realRoot
import skillbill.infrastructure.workflow.review.specialists.review.output
import skillbill.infrastructure.workflow.review.specialists.system.path
import skillbill.infrastructure.workflow.review.specialists.system.root
import skillbill.infrastructure.workflow.runtime.root
import skillbill.ports.diff.DiffResolverPort
import skillbill.ports.review.model.ReviewCheckpointFileIdentity
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import java.util.logging.Logger

private val diffResolverLog: Logger = Logger.getLogger(FileSystemDiffResolver::class.java.name)

@Inject
class FileSystemDiffResolver : DiffResolverPort {
  override fun reviewWorktreeFileIdentities(
    root: Path,
    paths: List<String>,
  ): Map<String, ReviewCheckpointFileIdentity> =
    root.toRealPath().let { realRoot -> paths.associateWith { checkpointFileIdentity(realRoot, it) } }

  override fun readDiff(path: Path, maxBytes: Long): String? = path.takeIf(Files::isRegularFile)
    ?.takeIf { Files.size(it) <= maxBytes }
    ?.let(Files::readString)
    ?.takeIf(String::isNotBlank)

  override fun runProcess(args: List<String>, workDir: Path): String? {
    val outputFile = Files.createTempFile("skillbill-diff", ".out")
    return try {
      val result = BoundedExternalProcessRunner.run(
        BoundedExternalProcessRequest(
          argv = args,
          workingDirectory = workDir,
          redirectOutputFile = outputFile,
          deadlineSeconds = PROCESS_TIMEOUT_SECONDS,
          outputCapBytes = null,
        ),
      )
      if (result.timedOut || result.launchFailure) {
        null
      } else if (result.exitCode in setOf(0, 1)) {
        val sizeBytes = Files.size(outputFile)
        if (sizeBytes > MAX_DIFF_BYTES) return null
        result.output
      } else {
        null
      }
    } catch (error: IOException) {
      diffResolverLog.log(
        Level.WARNING,
        "runProcess failed args=$args workDir=$workDir",
        error,
      )
      null
    } catch (error: InterruptedException) {
      Thread.currentThread().interrupt()
      null
    } finally {
      try {
        Files.deleteIfExists(outputFile)
      } catch (_: IOException) {
      }
    }
  }

  private companion object {
    const val PROCESS_TIMEOUT_SECONDS = 120L
    const val MAX_DIFF_BYTES = 50L * 1024 * 1024
  }
}
