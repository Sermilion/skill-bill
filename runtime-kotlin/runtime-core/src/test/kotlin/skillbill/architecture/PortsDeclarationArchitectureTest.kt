package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PortsDeclarationArchitectureTest {
  private val runtimeRoot: Path =
    Path.of("").toAbsolutePath().normalize().let { workingDir ->
      if (workingDir.fileName.toString().startsWith("runtime-")) workingDir.parent else workingDir
    }

  private val portsMainRoot: Path =
    runtimeRoot.resolve("runtime-kotlin/runtime-ports/src/main/kotlin")

  @Test
  fun `runtime-ports main source has no forbidden top-level objects classes casts or interface throw defaults`() {
    val violations = scanPortsMainSource()
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }

  @Test
  fun `scanner rejects synthetic fixtures for each forbidden ports-main pattern`() {
    val objectFixture =
      """
      package skillbill.ports.fixture

      object ForbiddenPortObject
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: object ForbiddenPortObject"),
      topLevelObjectViolations("Forbidden.kt", objectFixture),
    )

    val classFixture =
      """
      package skillbill.ports.fixture

      class ForbiddenPortClass
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: class ForbiddenPortClass"),
      forbiddenTopLevelClassViolations("Forbidden.kt", classFixture),
    )

    val castFixture =
      """
      package skillbill.ports.fixture

      fun cast(value: Any) = (this as String)
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: fun cast(value: Any) = (this as String)"),
      thisAsCastViolations("Forbidden.kt", castFixture),
    )

    val interfaceFixture =
      """
      package skillbill.ports.fixture

      interface ForbiddenPort {
        fun refuse(): Unit = error("not implemented")
      }
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: interface default body uses error or throw"),
      interfaceDefaultBodyViolations("Forbidden.kt", interfaceFixture),
    )
  }

  private fun scanPortsMainSource(): List<String> = buildList {
    if (!Files.isDirectory(portsMainRoot)) return@buildList
    Files.walk(portsMainRoot).use { paths ->
      paths.filter { Files.isRegularFile(it) && it.extension == "kt" }.forEach { path ->
        val source = Files.readString(path)
        val fileName = portsMainRoot.relativize(path).toString()
        addAll(topLevelObjectViolations(fileName, source))
        addAll(forbiddenTopLevelClassViolations(fileName, source))
        addAll(thisAsCastViolations(fileName, source))
        addAll(interfaceDefaultBodyViolations(fileName, source))
      }
    }
  }.sorted()

  private fun topLevelObjectViolations(fileName: String, source: String): List<String> =
    source.lineSequence()
      .map { it.trim() }
      .filter { line -> line.startsWith("object ") || line.startsWith("internal object ") }
      .map { line -> "$fileName: $line" }
      .toList()

  private fun forbiddenTopLevelClassViolations(fileName: String, source: String): List<String> =
    source.lineSequence()
      .map { it.trim() }
      .filter { line ->
        line.startsWith("class ") ||
          line.startsWith("internal class ") ||
          line.startsWith("abstract class ") ||
          line.startsWith("open class ")
      }
      .filterNot { line ->
        line.startsWith("data class ") ||
          line.startsWith("enum class ") ||
          line.startsWith("sealed class ") ||
          line.startsWith("value class ") ||
          line.startsWith("internal data class ") ||
          line.startsWith("internal enum class ") ||
          line.startsWith("internal sealed class ") ||
          line.startsWith("internal value class ")
      }
      .map { line -> "$fileName: $line" }
      .toList()

  private fun thisAsCastViolations(fileName: String, source: String): List<String> =
    source.lineSequence()
      .filter { line -> "(this as" in line }
      .map { line -> "$fileName: ${line.trim()}" }
      .toList()

  private fun interfaceDefaultBodyViolations(fileName: String, source: String): List<String> {
    val sourceWithoutCompanionBodies = removeCompanionBodies(source)
    val interfaceBlocks = Regex("""interface\s+\w+[^{]*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
      .findAll(sourceWithoutCompanionBodies)
      .map { it.groupValues[1] }
      .toList()
    return interfaceBlocks.flatMap { body ->
      if (body.contains("error(") || body.contains("throw ")) {
        listOf("$fileName: interface default body uses error or throw")
      } else {
        emptyList()
      }
    }
  }

  private fun removeCompanionBodies(source: String): String {
    val ranges = Regex("""companion\s+object\s*\{""")
      .findAll(source)
      .mapNotNull { match ->
        val closingBrace = matchingBrace(source, match.range.last)
        closingBrace?.let { match.range.first..it }
      }
      .toList()
    return ranges.asReversed().fold(source) { current, range -> current.removeRange(range) }
  }

  private fun matchingBrace(source: String, openingBrace: Int): Int? {
    var depth = 0
    for (index in openingBrace until source.length) {
      when (source[index]) {
        '{' -> depth += 1
        '}' -> {
          depth -= 1
          if (depth == 0) return index
        }
      }
    }
    return null
  }
}
