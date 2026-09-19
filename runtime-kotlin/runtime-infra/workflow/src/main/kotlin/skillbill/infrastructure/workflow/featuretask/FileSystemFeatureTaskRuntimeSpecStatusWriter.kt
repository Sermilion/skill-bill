package skillbill.infrastructure.workflow.featuretask
import me.tatarka.inject.annotations.Inject
import skillbill.infrastructure.workflow.filesystem.matches
import skillbill.infrastructure.workflow.git.goal.value
import skillbill.infrastructure.workflow.git.workflow.specPath
import skillbill.infrastructure.workflow.git.workflow.value
import skillbill.ports.taskruntime.FeatureTaskRuntimeSpecStatusWriter
import java.nio.file.Files
import java.nio.file.Path

@Inject
class FileSystemFeatureTaskRuntimeSpecStatusWriter : FeatureTaskRuntimeSpecStatusWriter {
  override fun writeFinalizingAgent(specPath: Path, finalizingAgentId: String) {
    val agentId = finalizingAgentId.trim()
    if (agentId.isEmpty()) {
      return
    }
    val normalizedPath = specPath.toAbsolutePath().normalize()
    if (!Files.isRegularFile(normalizedPath)) {
      return
    }
    val original = Files.readString(normalizedPath)
    val lines = original.split("\n").toMutableList()
    val statusHeadingIndex = lines.indexOfFirst { STATUS_HEADING.matches(it) }
    if (statusHeadingIndex < 0) {
      return
    }
    val sectionEnd = sectionEndExclusive(lines, statusHeadingIndex)
    val agentLineIndex = (statusHeadingIndex + 1 until sectionEnd).firstOrNull { AGENT_LINE.matches(lines[it]) }
    val statusLineIndex = (statusHeadingIndex + 1 until sectionEnd).firstOrNull { STATUS_LINE.matches(lines[it]) }
    val prefix = statusLineIndex?.let { bulletPrefix(lines[it]) } ?: "- "
    val agentLine = "${prefix}Agent: $agentId"
    when {
      agentLineIndex != null -> lines[agentLineIndex] = agentLine
      statusLineIndex != null -> lines.add(statusLineIndex + 1, agentLine)
      else -> lines.add(statusHeadingIndex + 1, agentLine)
    }
    val updated = lines.joinToString("\n")
    if (updated != original) {
      Files.writeString(normalizedPath, updated)
    }
  }

  private fun sectionEndExclusive(lines: List<String>, headingIndex: Int): Int {
    val next = (headingIndex + 1 until lines.size).firstOrNull { HEADING.matches(lines[it]) }
    return next ?: lines.size
  }

  private fun bulletPrefix(statusLine: String): String = BULLET_PREFIX.find(statusLine)?.value ?: ""

  private companion object {
    val HEADING = Regex("""^#{2,6}\s+.+$""")
    val STATUS_HEADING = Regex("""^#{2,6}\s+Status\b.*$""")
    val STATUS_LINE = Regex("""^\s*(?:[-*]\s+)?Status\s*:.*$""")
    val AGENT_LINE = Regex("""^\s*(?:[-*]\s+)?Agent\s*:.*$""")
    val BULLET_PREFIX = Regex("""^\s*(?:[-*]\s+)?""")
  }
}
