package skillbill.ports.scaffold.model

import java.nio.file.Path

data class GeneratedArtifactFile(
  val path: Path,
  val reason: String,
)

data class PilotedPlatformPackProjection(
  val slug: String,
  val displayName: String?,
)

data class ScaffoldRenderBlock(
  val header: String,
  val content: String,
)

data class ScaffoldRenderResult(
  val repoRoot: Path,
  val skillName: String,
  val blocks: List<ScaffoldRenderBlock>,
) {
  val stdout: String =
    buildString {
      blocks.forEachIndexed { index, block ->
        if (index > 0) {
          appendLine()
        }
        appendLine(block.header)
        append(block.content.trimEnd('\r', '\n'))
        appendLine()
      }
    }
}
