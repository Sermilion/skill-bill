package skillbill.architecture

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class PortsDeclarationArchitectureTest {
  private val portsMainRoot = moduleMainKotlinRoot("runtime-ports")

  @Test
  fun `runtime-ports main source has no forbidden top-level objects classes casts or interface throw defaults`() {
    val violations = scanPortsMainSource()
    assertEquals(emptyList(), violations, violations.joinToString("\n"))
  }

  @Test
  fun `scanner rejects synthetic fixtures for each forbidden ports-main pattern`() {
    val objectFixture =
      """

      object ForbiddenPortObject
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: object ForbiddenPortObject"),
      topLevelObjectViolations("Forbidden.kt", objectFixture),
    )

    val classFixture =
      """

      class ForbiddenPortClass
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: class ForbiddenPortClass"),
      forbiddenTopLevelClassViolations("Forbidden.kt", classFixture),
    )

    val castFixture =
      """

      fun cast(value: Any) = (this as String)
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: fun cast(value: Any) = (this as String)"),
      thisAsCastViolations("Forbidden.kt", castFixture),
    )

    val interfaceFixture =
      """

      interface ForbiddenPort {
        fun refuse(): Unit = error("not implemented")
      }
      """.trimIndent()
    assertEquals(
      listOf("Forbidden.kt: interface default body uses error or throw"),
      interfaceDefaultBodyViolations("Forbidden.kt", interfaceFixture),
    )
  }

  private fun scanPortsMainSource(): List<String> {
    val sourceFiles = kotlinFilesUnderWithArchitectureAsserts(portsMainRoot)
    return buildList {
      sourceFiles.forEach { path ->
        val source = Files.readString(path)
        val fileName = portsMainRoot.relativize(path).toString()
        addAll(topLevelObjectViolations(fileName, source))
        if (!fileName.endsWith("skillbill/ports/goalrunner/GoalParentProjectionWriter.kt")) {
          addAll(forbiddenTopLevelClassViolations(fileName, source))
        }
        addAll(thisAsCastViolations(fileName, source))
        addAll(interfaceDefaultBodyViolations(fileName, source))
      }
    }.sorted()
  }

  private fun topLevelObjectViolations(
    fileName: String,
    source: String,
  ): List<String> =
    source.lineSequence()
      .map { it.trim() }
      .filter { line ->
        Regex(
          """(?:(?:public|private|protected|internal)\s+)?(?<!data\s)object\s+[A-Za-z_][A-Za-z0-9_]*\b""",
        )
          .containsMatchIn(line)
      }
      .map { line -> "$fileName: $line" }
      .toList()

  private fun forbiddenTopLevelClassViolations(
    fileName: String,
    source: String,
  ): List<String> =
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

  private fun thisAsCastViolations(
    fileName: String,
    source: String,
  ): List<String> =
    source.lineSequence()
      .filter { line -> "(this as" in line }
      .map { line -> "$fileName: ${line.trim()}" }
      .toList()

  private fun interfaceDefaultBodyViolations(
    fileName: String,
    source: String,
  ): List<String> {
    val sourceWithoutCompanionBodies = removeCompanionBodies(source)
    val interfaceBlocks =
      Regex("""interface\s+\w+[^{]*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
        .findAll(sourceWithoutCompanionBodies)
        .map { it.groupValues[1] }
        .toList()
    return interfaceBlocks.flatMap { body ->
      if (
        Regex(
          """\bfun\s+\w+\s*\([^)]*\)[^={]*(?:=[^{]*\b(?:error|throw)\s*\(|\{[^}]*\b(?:error|throw)\b)""",
          RegexOption.DOT_MATCHES_ALL,
        ).containsMatchIn(body)
      ) {
        listOf("$fileName: interface default body uses error or throw")
      } else {
        emptyList()
      }
    }
  }

  private fun removeCompanionBodies(source: String): String {
    val ranges =
      Regex("""companion\s+object\s*\{""")
        .findAll(source)
        .mapNotNull { match ->
          val closingBrace = matchingBrace(source, match.range.last)
          closingBrace?.let { match.range.first..it }
        }
        .toList()
    return ranges.asReversed().fold(source) { current, range -> current.removeRange(range) }
  }

  private fun matchingBrace(
    source: String,
    openIndex: Int,
  ): Int? {
    var depth = 0
    for (index in openIndex until source.length) {
      when (source[index]) {
        '{' -> depth++
        '}' -> {
          depth--
          if (depth == 0) return index
        }
      }
    }
    return null
  }
}
