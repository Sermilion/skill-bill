package skillbill.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

object ArchitectureScanSupport {
  val runtimeRoot: Path =
    Path.of("").toAbsolutePath().normalize().let { start ->
      var dir: Path? = start
      while (dir != null) {
        if (Files.isDirectory(dir.resolve("runtime-kotlin"))) return@let dir
        dir = dir.parent
      }
      start
    }

  data class ParseBoundarySite(val relativePath: String, val functionNames: Set<String>)

  data class LineCeilingExemption(val relativePath: String, val reason: String)

  fun kotlinFilesUnder(root: Path): List<Path> {
    if (!Files.isDirectory(root)) {
      error(
        "Missing architecture scan root: ${scanRootLabel(root)}",
      )
    }
    return Files.walk(root).use { paths ->
      paths
        .filter { path ->
          path.isRegularFile() &&
            path.extension == "kt" &&
            !path.toString().replace('\\', '/').contains("/build/")
        }
        .toList()
    }
  }

  fun declaredPackage(source: String): String? = PACKAGE_PATTERN.find(source)?.groupValues?.get(1)

  fun primaryTopLevelDeclarationName(source: String): String? {
    var braceDepth = 0
    source.lineSequence().forEach { rawLine ->
      val line = rawLine.withoutCommentText().text
      if (braceDepth == 0) {
        TOP_LEVEL_DECLARATION_PATTERN.find(line)?.groupValues?.get(2)?.let { return it }
      }
      braceDepth += line.count { character -> character == '{' }
      braceDepth -= line.count { character -> character == '}' }
      if (braceDepth < 0) braceDepth = 0
    }
    return null
  }

  fun packageClusteringViolations(
    sourceRoots: List<String>,
    genericSegments: Set<String>,
  ): List<String> {
    val filesByPackage = linkedMapOf<String, MutableList<Pair<Path, String>>>()
    sourceRoots.forEach { sourceRoot ->
      kotlinFilesUnder(runtimeRoot.resolve(sourceRoot)).forEach { sourceFile ->
        val source = sourceFile.readText()
        val packageName = declaredPackage(source) ?: return@forEach
        val primaryName = primaryTopLevelDeclarationName(source) ?: sourceFile.fileName.toString().removeSuffix(".kt")
        filesByPackage.getOrPut(packageName) { mutableListOf() }.add(sourceFile to primaryName)
      }
    }
    val allPackages = filesByPackage.keys
    val violations = mutableListOf<String>()
    filesByPackage.forEach { (packageName, looseFiles) ->
      val childPackages =
        allPackages.filter { child ->
          child.startsWith("$packageName.") && "." !in child.removePrefix("$packageName.")
        }
      if (childPackages.isEmpty()) return@forEach
      val areaChildren =
        childPackages
          .map { child -> child.substringAfterLast('.') }
          .filterNot { segment -> segment in genericSegments }
          .toSet()
      if (areaChildren.isEmpty()) return@forEach
      val parentNoun = packageName.substringAfterLast('.')
      looseFiles.forEach { (sourceFile, primaryName) ->
        val matchedChild = matchingAreaChild(primaryName, areaChildren, parentNoun)
        if (matchedChild != null) {
          violations += "${runtimeRoot.relativize(sourceFile)} in $packageName belongs to cluster '$matchedChild'; " +
            "move it under $packageName.$matchedChild or a deeper cluster package."
        }
      }
    }
    return violations.sorted()
  }

  fun productionLineCeilingViolations(
    productionRoots: List<String>,
    ceiling: Int,
    exemptions: Map<String, String>,
  ): List<String> {
    val violations = mutableListOf<String>()
    productionRoots.forEach { productionRoot ->
      kotlinFilesUnder(runtimeRoot.resolve(productionRoot)).forEach { sourceFile ->
        val relativePath = runtimeRoot.relativize(sourceFile).toString().replace('\\', '/')
        if (isNonProductionKotlinSourceSet(relativePath)) return@forEach
        violations +=
          productionLineCeilingViolationsInSource(
            relativePath = relativePath,
            source = sourceFile.readText(),
            ceiling = ceiling,
            exemptions = exemptions,
          )
      }
    }
    return violations.sorted()
  }

  fun productionLineCeilingViolationsInSource(
    relativePath: String,
    source: String,
    ceiling: Int,
    exemptions: Map<String, String>,
  ): List<String> {
    val lineCount = source.lineSequence().count()
    if (lineCount <= ceiling || relativePath in exemptions) return emptyList()
    return listOf(
      "$relativePath has $lineCount lines; split it below the $ceiling-line ceiling " +
        "or add an explicit exemption with reason.",
    )
  }

  fun isNonProductionKotlinSourceSet(relativePath: String): Boolean {
    val normalized = relativePath.replace('\\', '/')
    return "/src/test/" in normalized ||
      "/src/testFixtures/" in normalized ||
      "/src/repoTest/" in normalized ||
      "/src/androidTest/" in normalized ||
      "/src/androidUnitTest/" in normalized ||
      "/src/commonTest/" in normalized ||
      "/src/jvmTest/" in normalized ||
      "/src/nativeTest/" in normalized
  }

  fun inlineFqnViolations(
    scanRoots: List<String>,
    prefixes: List<String>,
  ): List<String> {
    val violations = mutableListOf<String>()
    scanRoots.forEach { scanRoot ->
      kotlinFilesUnder(runtimeRoot.resolve(scanRoot)).forEach { sourceFile ->
        val relativePath = runtimeRoot.relativize(sourceFile).toString().replace('\\', '/')
        if (relativePath.contains("/build/generated/")) return@forEach
        inlineFqnReferences(sourceFile.readText(), prefixes).forEach { reference ->
          violations += "$relativePath contains inline reference $reference; import the type or add a documented alias."
        }
      }
    }
    return violations.sorted()
  }

  fun parseBoundaryViolations(sites: List<ParseBoundarySite>): List<String> {
    val violations = mutableListOf<String>()
    sites.forEach { site ->
      val sourceFile = runtimeRoot.resolve(site.relativePath)
      val source = sourceFile.readText()
      extractFunctionBodies(source, site.functionNames).forEach { (functionName, body) ->
        forbiddenParseBoundaryReporter(body).forEach { reporter ->
          violations += "${site.relativePath}::$functionName reports malformed external input via $reporter; " +
            "use a typed contract failure instead."
        }
      }
    }
    return violations.sorted()
  }

  fun conventionReapplicationViolations(
    moduleBuildFiles: List<Path>,
    ownedPatterns: List<Pair<String, String>>,
  ): List<String> {
    val violations = mutableListOf<String>()
    moduleBuildFiles.forEach { buildFile ->
      val relativePath = runtimeRoot.relativize(buildFile).toString().replace('\\', '/')
      violations +=
        conventionReapplicationViolationsInText(
          relativePath = relativePath,
          text = buildFile.readText(),
          ownedPatterns = ownedPatterns,
        )
    }
    return violations.sorted()
  }

  fun conventionReapplicationViolationsInText(
    relativePath: String,
    text: String,
    ownedPatterns: List<Pair<String, String>>,
  ): List<String> {
    val violations = mutableListOf<String>()
    ownedPatterns.forEach { (pattern, settingName) ->
      if (Regex(pattern).containsMatchIn(text)) {
        violations +=
          "$relativePath re-applies $settingName already owned by configureKotlinJvm " +
          "via skillbill.jvm-library."
      }
    }
    return violations.sorted()
  }

  fun inlineFqnReferences(
    source: String,
    prefixes: List<String>,
  ): List<String> =
    sourceWithoutStringLiterals(source)
      .lineSequence()
      .filterNot { line ->
        val trimmed = line.trim()
        trimmed.startsWith("import ") ||
          trimmed.startsWith("package ") ||
          trimmed.startsWith("//") ||
          trimmed.startsWith("*") ||
          trimmed.startsWith("/*")
      }
      .flatMap { line ->
        val codeLine = line.substringBefore("//")
        prefixes.flatMap { prefix -> inlineFqnMatches(codeLine, prefix) }
      }
      .distinct()
      .sorted()
      .toList()

  private fun sourceWithoutStringLiterals(source: String): String {
    val output = StringBuilder()
    var index = 0
    while (index < source.length) {
      when {
        source.startsWith("\"\"\"", index) -> {
          val end = source.indexOf("\"\"\"", index + 3)
          if (end == -1) {
            output.append("\"\"\"")
            break
          }
          output.append("\"\"\"")
          index = end + 3
        }
        source[index] == '"' -> {
          output.append('"')
          index++
          while (index < source.length) {
            when (source[index]) {
              '\\' -> index += 2
              '"' -> {
                output.append('"')
                index++
                break
              }
              else -> index++
            }
          }
        }
        else -> {
          output.append(source[index])
          index++
        }
      }
    }
    return output.toString()
  }

  private fun inlineFqnMatches(
    line: String,
    prefix: String,
  ): List<String> {
    val escaped = Regex.escape(prefix)
    val pattern =
      when (prefix) {
        "java.",
        "javax.",
        "jakarta.",
        "kotlin.",
        "kotlinx.",
        -> Regex("""\b$escaped(?:[a-z][a-z0-9]*\.)+[A-Z][A-Za-z0-9_]*(?:\.[a-z][A-Za-z0-9_]*)*\b""")
        else -> Regex("""\b$escaped(?:[a-z][a-z0-9]*\.)+[A-Z][A-Za-z0-9_]*(?:\.[a-zA-Z][A-Za-z0-9_]*)*\b""")
      }
    return pattern.findAll(line)
      .map { match -> match.value }
      .filter { reference -> reference.count { character -> character == '.' } >= 2 }
      .toList()
  }

  private fun forbiddenParseBoundaryReporter(body: String): List<String> {
    val reporters = mutableListOf<String>()
    if (Regex("""\berror\s*\(""").containsMatchIn(body)) reporters += "error()"
    if (Regex("""\brequire\s*\(""").containsMatchIn(body)) reporters += "require()"
    if (Regex("""\bthrow\s+IllegalArgumentException\b""").containsMatchIn(body)) {
      reporters += "throw IllegalArgumentException"
    }
    if (Regex("""\bthrow\s+RuntimeException\b""").containsMatchIn(body)) reporters += "throw RuntimeException"
    return reporters
  }

  private fun extractFunctionBodies(
    source: String,
    functionNames: Set<String>,
  ): Map<String, String> {
    val bodies = linkedMapOf<String, String>()
    var braceDepth = 0
    var captureStartDepth = -1
    var capturingName: String? = null
    val capture = StringBuilder()
    source.lineSequence().forEach { rawLine ->
      val line = rawLine.withoutCommentText().text
      if (capturingName == null) {
        val match = FUNCTION_PATTERN.find(line)
        if (match != null && match.groupValues[1] in functionNames) {
          capturingName = match.groupValues[1]
          captureStartDepth = braceDepth
          capture.clear()
        }
      }
      if (capturingName != null) {
        capture.appendLine(rawLine)
        braceDepth += line.count { character -> character == '{' }
        braceDepth -= line.count { character -> character == '}' }
        if (braceDepth <= captureStartDepth && line.contains('}')) {
          bodies[capturingName] = capture.toString()
          capturingName = null
          captureStartDepth = -1
          capture.clear()
        }
      } else {
        braceDepth += line.count { character -> character == '{' }
        braceDepth -= line.count { character -> character == '}' }
        if (braceDepth < 0) braceDepth = 0
      }
    }
    return bodies
  }

  fun parseBoundaryViolationsInSource(
    source: String,
    site: ParseBoundarySite,
  ): List<String> {
    val violations = mutableListOf<String>()
    extractFunctionBodies(source, site.functionNames).forEach { (functionName, body) ->
      forbiddenParseBoundaryReporter(body).forEach { reporter ->
        violations += "${site.relativePath}::$functionName reports malformed external input via $reporter; " +
          "use a typed contract failure instead."
      }
    }
    return violations.sorted()
  }

  fun packageClusteringViolationMessage(
    packageName: String,
    primaryName: String,
    areaChildren: Set<String>,
    genericSegments: Set<String>,
  ): String? {
    val parentNoun = packageName.substringAfterLast('.')
    val filteredChildren = areaChildren.filterNot { segment -> segment in genericSegments }.toSet()
    if (filteredChildren.isEmpty()) return null
    val matchedChild = matchingAreaChild(primaryName, filteredChildren, parentNoun) ?: return null
    return "$packageName/$primaryName.kt belongs to cluster '$matchedChild'; move it under $packageName.$matchedChild."
  }

  data class PackageSiblingCount(
    val packageName: String,
    val fileCount: Int,
    val ceiling: Int,
  )

  fun productionPackageSiblingCounts(sourceRoots: List<String>): List<PackageSiblingCount> {
    val counts = linkedMapOf<String, Int>()
    sourceRoots.forEach { sourceRoot ->
      kotlinFilesUnder(runtimeRoot.resolve(sourceRoot)).forEach { sourceFile ->
        declaredPackage(sourceFile.readText())?.let { packageName ->
          counts[packageName] = (counts[packageName] ?: 0) + 1
        }
      }
    }
    return counts.map { (packageName, fileCount) ->
      PackageSiblingCount(
        packageName = packageName,
        fileCount = fileCount,
        ceiling = if (packageName.substringAfterLast('.') == "model") 20 else 12,
      )
    }.sortedBy { it.packageName }
  }

  fun productionPackageSiblingCountViolations(
    sourceRoots: List<String>,
    remainderInventory: Set<String>,
  ): List<String> =
    productionPackageSiblingCountViolationsForCounts(
      counts = productionPackageSiblingCounts(sourceRoots),
      remainderInventory = remainderInventory,
    )

  fun productionPackageSiblingCountViolationsForCounts(
    counts: List<PackageSiblingCount>,
    remainderInventory: Set<String>,
  ): List<String> =
    counts
      .filter { count -> count.fileCount > count.ceiling && count.packageName !in remainderInventory }
      .map { count -> packageSiblingCountViolationMessage(count) }

  fun packageSiblingCountViolationMessage(
    packageName: String,
    fileCount: Int,
  ): String? {
    val ceiling = if (packageName.substringAfterLast('.') == "model") 20 else 12
    if (fileCount <= ceiling) return null
    return packageSiblingCountViolationMessage(
      PackageSiblingCount(packageName, fileCount, ceiling),
    )
  }

  private fun packageSiblingCountViolationMessage(count: PackageSiblingCount): String =
    "${count.packageName} has ${count.fileCount} production Kotlin siblings; " +
      "the ${count.ceiling}-file ceiling applies to this package."

  private fun matchingAreaChild(
    primaryName: String,
    areaChildren: Set<String>,
    parentNoun: String,
  ): String? {
    val normalized = camelTokens(primaryName).joinToString("")
    return areaChildren.firstOrNull { area ->
      area != parentNoun &&
        (
          normalized.contains(area) ||
            area in primaryName.lowercase() ||
            primaryName.lowercase().contains(area)
        )
    }
  }

  private fun camelTokens(name: String): List<String> =
    CAMEL_TOKEN_PATTERN.findAll(name).map { match -> match.value.lowercase() }.toList()

  private data class SourceLine(val text: String)

  private fun String.withoutCommentText(): SourceLine {
    var remaining = this
    val output = StringBuilder()
    while (remaining.isNotEmpty()) {
      val next = nextCommentBoundary(remaining)
      if (next == null) {
        output.append(remaining)
        remaining = ""
      } else {
        output.append(remaining.take(next.start))
        remaining =
          if (next.isLineComment) {
            ""
          } else {
            remaining.drop(next.endExclusive)
          }
      }
    }
    return SourceLine(output.toString())
  }

  private data class CommentBoundary(val start: Int, val endExclusive: Int, val isLineComment: Boolean)

  private fun nextCommentBoundary(remaining: String): CommentBoundary? {
    val lineComment = remaining.indexOf("//").takeUnless { index -> index == -1 } ?: remaining.length
    val blockComment = remaining.indexOf("/*").takeUnless { index -> index == -1 } ?: remaining.length
    if (lineComment == remaining.length && blockComment == remaining.length) return null
    if (lineComment <= blockComment) {
      return CommentBoundary(lineComment, remaining.length, isLineComment = true)
    }
    val end = remaining.indexOf("*/", blockComment + 2)
    if (end == -1) {
      return CommentBoundary(blockComment, remaining.length, isLineComment = false)
    }
    return CommentBoundary(blockComment, end + 2, isLineComment = false)
  }

  private val PACKAGE_PATTERN = Regex("""^\s*package\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
  private val IMPORT_PATTERN = Regex("""^\s*import\s+([A-Za-z0-9_.]+)""", RegexOption.MULTILINE)
  private val TOP_LEVEL_DECLARATION_PATTERN =
    Regex(
      """^\s*((?:(?:public|internal|private|protected|abstract|sealed|open|final|data|enum|value|fun)\s+)*)""" +
        """(?:class|object|interface|fun)\s+([A-Za-z_][A-Za-z0-9_]*)\b""",
    )
  private val SPILLOVER_NUMBERED_SUFFIX_PATTERN =
    Regex("""(?:Extras\d*|Continued\d*|Helpers\d+|Fns\d+|Support\d+|Misc\d+|(?<![A-Z])[A-Z]\d+)$""")
  private val SPILLOVER_MAIN_SOURCE_SUFFIX_PATTERN =
    Regex("""(?:Extras\d*|Continued\d*|Helpers\d*|Fns\d+|Support\d*|Misc\d*|(?<![A-Z])[A-Z]\d+)$""")
  private val SPILLOVER_DECLARATION_PATTERN =
    Regex(
      """^\s*(?:(?:public|internal|private|protected|abstract|sealed|open|final|data|enum|value|inline|operator|""" +
        """infix|suspend|override|lateinit|const|annotation|inner|companion|external|tailrec|expect|actual|""" +
        """fun)\s+)*(?:class|object|interface|fun|val|var)\s+(.*)$""",
    )
  private val FUNCTION_PATTERN = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
  private val ABSTRACT_PROPERTY_PATTERN =
    Regex(
      """^\s*(?:(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)\n]*\))?|public|internal|protected)\s+)*""" +
        """abstract\s+(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)\s*:""",
      RegexOption.MULTILINE,
    )
  private val CAMEL_TOKEN_PATTERN = Regex("""[A-Z]?[a-z]+|[A-Z]+(?=[A-Z][a-z]|\b)""")
  private val EXTENSION_FUN_PATTERN =
    Regex("""^\s*(?:(?:public|internal|private|protected)\s+)*fun\s+([A-Za-z0-9_.]+)\.""")

  data class PackageCycle(val areas: List<String>)

  data class AmbientCallSite(val relativePath: String, val lineNumber: Int, val call: String)

  data class InjectConstructorDefaultSite(val relativePath: String, val symbol: String, val parameter: String)

  fun declaredImports(source: String): List<String> =
    IMPORT_PATTERN.findAll(source).map { match -> match.groupValues[1] }.toList()

  fun abstractPropertyNames(source: String): Set<String> =
    ABSTRACT_PROPERTY_PATTERN.findAll(source).map { match -> match.groupValues[1] }.toSet()

  private val COMPOSITION_FUN_DECL_PATTERN =
    Regex(
      """^\s*((?:(?:public|internal|private|protected|open|abstract|final|override|suspend|inline|infix|""" +
        """operator|tailrec|external|expect|actual)\s+)*)fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""",
      RegexOption.MULTILINE,
    )

  private fun compositionPrefixHasProvidesAnnotation(prefix: String): Boolean {
    var lineEnd = prefix.length
    while (lineEnd > 0) {
      while (lineEnd > 0 && prefix[lineEnd - 1].isWhitespace()) lineEnd -= 1
      if (lineEnd == 0) return false
      val lineStart = prefix.lastIndexOf('\n', lineEnd - 1) + 1
      val line = prefix.substring(lineStart, lineEnd).trim()
      if (!line.startsWith("@")) return false
      if (line.contains("@Provides")) return true
      lineEnd = lineStart
    }
    return false
  }

  fun unclassifiedPublicFunctionNames(source: String): Set<String> {
    val names = linkedSetOf<String>()
    COMPOSITION_FUN_DECL_PATTERN.findAll(source).forEach { match ->
      val modifiers = match.groupValues[1]
      val name = match.groupValues[2]
      if (modifiers.split(Regex("""\s+""")).any { modifier ->
          modifier in setOf("internal", "private", "protected")
        }
      ) {
        return@forEach
      }
      if (compositionPrefixHasProvidesAnnotation(source.substring(0, match.range.first))) return@forEach
      names += name
    }
    return names
  }

  fun runtimeComponentCompositionSurfaceSources(diScanRoot: String): List<Path> {
    val diRoot = runtimeRoot.resolve(diScanRoot)
    val componentFile = runtimeRoot.resolve(PrincipleEnforcementInventory.RUNTIME_COMPONENT_SOURCE)
    val providesFiles =
      kotlinFilesUnder(diRoot).filter { path ->
        val fileName = path.fileName.toString()
        fileName.startsWith("Runtime") && fileName.endsWith("Provides.kt")
      }
    return (listOfNotNull(componentFile.takeIf { Files.exists(it) }) + providesFiles).distinct()
  }

  fun runtimeComponentPublicCallableViolations(diScanRoot: String): List<String> =
    runtimeComponentCompositionSurfaceSources(diScanRoot)
      .flatMap { sourceFile ->
        val relativePath = runtimeRoot.relativize(sourceFile).toString().replace('\\', '/')
        unclassifiedPublicFunctionNames(sourceFile.readText()).map { name ->
          "$relativePath exposes public function '$name' outside the @Provides generated-wiring surface."
        }
      }
      .sorted()

  fun runtimeComponentPublicCallableViolationsInSource(
    relativePath: String,
    source: String,
  ): List<String> =
    unclassifiedPublicFunctionNames(source).map { name ->
      "$relativePath exposes public function '$name' outside the @Provides generated-wiring surface."
    }

  fun logicalTypeLineCounts(productionRoots: List<String>): Map<String, Int> =
    productionRoots
      .flatMap { productionRoot -> kotlinFilesUnder(runtimeRoot.resolve(productionRoot)) }
      .mapNotNull { sourceFile ->
        val relativePath = runtimeRoot.relativize(sourceFile).toString().replace('\\', '/')
        if (isNonProductionKotlinSourceSet(relativePath)) return@mapNotNull null
        val source = sourceFile.readText()
        val packageName = declaredPackage(source) ?: return@mapNotNull null
        SourceFile(
          relativePath = relativePath,
          packageName = packageName,
          imports = declaredImports(source),
          source = source,
        )
      }
      .let(::logicalTypeLineCountsInSources)

  internal fun logicalTypeLineCeilingViolationsInSources(
    sourceFiles: List<SourceFile>,
    ceiling: Int,
    baseline: Map<String, Int>,
  ): List<String> =
    logicalTypeLineCeilingViolationsForCounts(
      logicalTypeLineCountsInSources(sourceFiles),
      ceiling,
      baseline,
    )

  fun logicalTypeLineCeilingViolations(
    productionRoots: List<String>,
    ceiling: Int,
    baseline: Map<String, Int>,
  ): List<String> =
    logicalTypeLineCeilingViolationsForCounts(
      logicalTypeLineCounts(productionRoots),
      ceiling,
      baseline,
    )

  private fun logicalTypeLineCountsInSources(sourceFiles: List<SourceFile>): Map<String, Int> {
    val counts = linkedMapOf<String, Int>()
    sourceFiles.forEach { sourceFile ->
      val lineCount = sourceFile.source.lineSequence().count()
      val targets =
        ArchitectureScanSupport.primaryTopLevelDeclarationName(sourceFile.source)?.let { topLevelType ->
          listOf("${sourceFile.packageName}.$topLevelType")
        } ?: extensionReceiverFqns(sourceFile.source, sourceFile.packageName, sourceFile.imports)
      targets.distinct().forEach { fqn ->
        counts[fqn] = counts.getOrDefault(fqn, 0) + lineCount
      }
    }
    return counts
  }

  private fun logicalTypeLineCeilingViolationsForCounts(
    counts: Map<String, Int>,
    ceiling: Int,
    baseline: Map<String, Int>,
  ): List<String> {
    val violations = mutableListOf<String>()
    counts.forEach { (fqn, lineCount) ->
      val baselineCount = baseline[fqn]
      when {
        baselineCount != null && lineCount > baselineCount ->
          violations += "$fqn has $lineCount lines; baseline allows $baselineCount."
        baselineCount == null && lineCount > ceiling ->
          violations +=
            "$fqn has $lineCount lines; exceeds the $ceiling-line ceiling without a baseline entry."
      }
    }
    return violations.sorted()
  }

  fun parseIntBaseline(text: String): Map<String, Int> =
    text.lineSequence()
      .map { it.trim() }
      .filter { it.isNotBlank() && !it.startsWith("#") }
      .mapNotNull { line ->
        val parts = line.split(Regex("""\s+"""), limit = 2)
        if (parts.size != 2) return@mapNotNull null
        val count = parts[1].toIntOrNull() ?: return@mapNotNull null
        parts[0] to count
      }
      .toMap()

  fun packageImportEdges(
    scanRoot: String,
    packagePrefix: String,
  ): Map<String, Set<String>> {
    val edges = linkedMapOf<String, MutableSet<String>>()
    kotlinFilesUnder(runtimeRoot.resolve(scanRoot)).forEach { sourceFile ->
      val source = sourceFile.readText()
      val packageName = declaredPackage(source) ?: return@forEach
      if (!packageName.startsWith(packagePrefix)) return@forEach
      val area = packageName.removePrefix(packagePrefix).substringBefore('.')
      if (area.isBlank()) return@forEach
      declaredImports(source)
        .filter { it.startsWith(packagePrefix) }
        .map { imported -> imported.removePrefix(packagePrefix).substringBefore('.') }
        .filter { it.isNotBlank() && it != area }
        .forEach { importedArea -> edges.getOrPut(area) { mutableSetOf() }.add(importedArea) }
    }
    return edges.mapValues { (_, value) -> value.toSet() }
  }

  fun packageCycles(
    scanRoot: String,
    packagePrefix: String,
  ): Set<PackageCycle> =
    mutualImportCyclesForEdges(packageImportEdges(scanRoot, packagePrefix))
      .map { cycle -> PackageCycle(cycle) }
      .toSet()

  fun packageCycleViolations(
    baselineCycles: Set<PackageCycle>,
    scanRoot: String,
    packagePrefix: String,
  ): List<String> = packageCycleViolationsForEdges(packageImportEdges(scanRoot, packagePrefix), baselineCycles)

  fun packageCycleViolationsForEdges(
    edges: Map<String, Set<String>>,
    baselineCycles: Set<PackageCycle>,
  ): List<String> {
    val baselineKeys = baselineCycles.map { cycle -> cycle.areas.sorted().joinToString("|") }.toSet()
    val currentKeys =
      mutualImportCyclesForEdges(edges)
        .map { cycle -> cycle.sorted().joinToString("|") }
        .toSet()
    return (currentKeys - baselineKeys).sorted().map { cycle ->
      "New package cycle not in baseline: ${cycle.replace("|", " <-> ")}"
    }
  }

  private fun mutualImportCyclesForEdges(edges: Map<String, Set<String>>): List<List<String>> {
    val cycles = linkedSetOf<List<String>>()
    edges.forEach { (from, targets) ->
      targets.forEach { to ->
        if (from != to && edges[to]?.contains(from) == true) {
          cycles += listOf(from, to).sorted()
        }
      }
    }
    return cycles.toList()
  }

  fun parsePackageCycleBaseline(text: String): Set<PackageCycle> =
    text.lineSequence()
      .map { it.trim() }
      .filter { it.isNotBlank() && !it.startsWith("#") }
      .map { line ->
        PackageCycle(line.split('|').map(String::trim).filter(String::isNotBlank).sorted())
      }
      .toSet()

  fun transitiveAreaClosure(
    edges: Map<String, Set<String>>,
    area: String,
  ): Set<String> {
    val reached = linkedSetOf<String>()
    val pending = ArrayDeque(listOf(area))
    while (pending.isNotEmpty()) {
      edges[pending.removeFirst()].orEmpty().forEach { next ->
        if (reached.add(next)) pending.addLast(next)
      }
    }
    return reached - area
  }

  fun areaIsolationViolationsForEdges(
    edges: Map<String, Set<String>>,
    area: String,
    sharedAreas: Set<String>,
  ): List<String> =
    transitiveAreaClosure(edges, area)
      .filterNot { reached -> reached in sharedAreas }
      .sorted()
      .map { reached ->
        "Area '$area' transitively imports '$reached'; only the shared leaves " +
          "(${sharedAreas.sorted().joinToString(", ")}) may appear in a single area's import closure."
      }

  fun allAreaIsolationViolationsForEdges(
    edges: Map<String, Set<String>>,
    sharedAreas: Set<String>,
    compositionRootArea: String,
  ): List<String> =
    (edges.keys + edges.values.flatten())
      .asSequence()
      .filterNot { area -> area == compositionRootArea }
      .distinct()
      .sorted()
      .flatMap { area -> areaIsolationViolationsForEdges(edges, area, sharedAreas).asSequence() }
      .toList()

  fun areaIsolationViolations(
    scanRoot: String,
    packagePrefix: String,
    sharedAreas: Set<String>,
    compositionRootArea: String,
  ): List<String> =
    allAreaIsolationViolationsForEdges(
      packageImportEdges(scanRoot, packagePrefix),
      sharedAreas,
      compositionRootArea,
    )

  fun projectEdgesForConfiguration(
    source: String,
    configuration: String,
  ): Set<String> {
    val edges = mutableSetOf<String>()
    val pattern =
      Regex(
        """^\s*${Regex.escape(configuration)}\(project\(":([A-Za-z0-9:-]+)"\)\)""",
        RegexOption.MULTILINE,
      )
    pattern.findAll(source).forEach { match -> edges += match.groupValues[1] }
    return edges
  }

  fun spilloverFileNameViolationsForPaths(
    relativePaths: List<String>,
    exemptPaths: Set<String>,
  ): List<String> {
    val pathsByPackage =
      relativePaths.groupBy { relativePath ->
        relativePath.replace('\\', '/').substringBeforeLast('/', missingDelimiterValue = "")
      }
    return relativePaths
      .filterNot { relativePath -> relativePath in exemptPaths }
      .filter { relativePath ->
        val normalized = relativePath.replace('\\', '/')
        val packageDir = normalized.substringBeforeLast('/', missingDelimiterValue = "")
        val baseName = normalized.substringAfterLast('/').removeSuffix(".kt")
        val siblings =
          pathsByPackage.getOrDefault(packageDir, emptyList())
            .map { path -> path.replace('\\', '/').substringAfterLast('/').removeSuffix(".kt") }
            .toSet()
        isSpilloverBaseName(baseName, siblings, isMainSourcePath(normalized))
      }
      .sorted()
      .map { relativePath ->
        "$relativePath carries the spillover-filename signature; name the unit for the responsibility it holds."
      }
  }

  private fun isMainSourcePath(normalizedPath: String): Boolean = "/src/main/" in normalizedPath

  private fun spilloverSuffixPattern(mainSource: Boolean): Regex =
    if (mainSource) SPILLOVER_MAIN_SOURCE_SUFFIX_PATTERN else SPILLOVER_NUMBERED_SUFFIX_PATTERN

  private fun isSpilloverBaseName(
    baseName: String,
    siblings: Set<String>,
    mainSource: Boolean,
  ): Boolean =
    when {
      spilloverSuffixPattern(mainSource).containsMatchIn(baseName) -> true
      else -> {
        val prefix = Regex("""^(.+?)(\d+)$""").find(baseName)?.groupValues?.get(1).orEmpty()
        prefix.isNotEmpty() && (
          prefix in siblings ||
            siblings.any { sibling ->
              sibling != baseName &&
                Regex("""^${Regex.escape(prefix)}\d+$""").matches(sibling)
            }
        )
      }
    }

  fun spilloverFileNamePaths(
    scanRoots: List<String>,
    exemptPaths: Set<String>,
  ): Set<String> =
    spilloverFileNameViolations(scanRoots, exemptPaths)
      .map { violation -> violation.substringBefore(' ') }
      .toSet()

  fun spilloverFileNameViolations(
    scanRoots: List<String>,
    exemptPaths: Set<String>,
  ): List<String> =
    spilloverFileNameViolationsForPaths(
      scanRoots.flatMap { scanRoot ->
        kotlinFilesUnder(runtimeRoot.resolve(scanRoot)).map { path ->
          runtimeRoot.relativize(path).toString().replace('\\', '/')
        }
      },
      exemptPaths,
    )

  fun spilloverIdentifierKeysInSource(
    relativePath: String,
    source: String,
  ): List<String> {
    val normalized = relativePath.replace('\\', '/')
    if (!isMainSourcePath(normalized)) return emptyList()
    return sourceWithoutStringLiterals(source).lineSequence()
      .mapNotNull { rawLine -> spilloverDeclarationName(rawLine.withoutCommentText().text) }
      .filter { name -> SPILLOVER_MAIN_SOURCE_SUFFIX_PATTERN.containsMatchIn(name) }
      .map { name -> "$normalized#$name" }
      .toList()
  }

  fun spilloverIdentifierViolationsInSource(
    relativePath: String,
    source: String,
  ): List<String> =
    spilloverIdentifierKeysInSource(relativePath, source).map { key ->
      "$key carries the spillover-identifier signature; name the declaration for the responsibility it holds."
    }

  fun spilloverIdentifierKeys(
    scanRoots: List<String>,
    exemptPaths: Set<String>,
  ): Set<String> =
    scanRoots.flatMap { scanRoot ->
      kotlinFilesUnder(runtimeRoot.resolve(scanRoot)).flatMap { path ->
        val relativePath = runtimeRoot.relativize(path).toString().replace('\\', '/')
        if (relativePath in exemptPaths) emptyList() else spilloverIdentifierKeysInSource(relativePath, path.readText())
      }
    }.toSet()

  private fun spilloverDeclarationName(line: String): String? {
    val remainder = SPILLOVER_DECLARATION_PATTERN.find(line)?.groupValues?.get(1)?.trimStart() ?: return null
    var index = if (remainder.startsWith('<')) skipBalancedAngles(remainder, 0) else 0
    while (true) {
      val start = skipWhitespace(remainder, index)
      val end = identifierEnd(remainder, start)
      if (end == start) return null
      index = skipTypeArgumentsAndNullability(remainder, end)
      if (index >= remainder.length || remainder[index] != '.') return remainder.substring(start, end)
      index++
    }
  }

  private fun skipWhitespace(
    text: String,
    from: Int,
  ): Int {
    var index = from
    while (index < text.length && text[index].isWhitespace()) index++
    return index
  }

  private fun identifierEnd(
    text: String,
    from: Int,
  ): Int {
    var index = from
    while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) index++
    return index
  }

  private fun skipTypeArgumentsAndNullability(
    text: String,
    from: Int,
  ): Int {
    var index = from
    if (index < text.length && text[index] == '<') index = skipBalancedAngles(text, index)
    if (index < text.length && text[index] == '?') index++
    return index
  }

  private fun skipBalancedAngles(
    text: String,
    openIndex: Int,
  ): Int {
    var depth = 0
    var index = openIndex
    while (index < text.length) {
      when (text[index]) {
        '<' -> depth++
        '>' -> if (--depth == 0) return index + 1
      }
      index++
    }
    return index
  }

  fun extensionReceiverFqns(
    source: String,
    packageName: String,
    imports: List<String>,
  ): List<String> {
    val importMap = imports.associateBy { imported -> imported.substringAfterLast('.') }
    val receivers = linkedSetOf<String>()
    var braceDepth = 0
    source.lineSequence().forEach { rawLine ->
      val line = rawLine.withoutCommentText().text
      if (braceDepth == 0) {
        EXTENSION_FUN_PATTERN.find(line)?.groupValues?.get(1)?.let { receiverType ->
          resolveTypeFqn(receiverType, packageName, importMap)?.let(receivers::add)
        }
      }
      braceDepth += line.count { character -> character == '{' }
      braceDepth -= line.count { character -> character == '}' }
      if (braceDepth < 0) braceDepth = 0
    }
    return receivers.toList()
  }

  private fun resolveTypeFqn(
    typeName: String,
    packageName: String,
    importMap: Map<String, String>,
  ): String? =
    when {
      '.' in typeName -> typeName
      typeName in importMap -> importMap.getValue(typeName)
      else -> "$packageName.$typeName"
    }

  data class AuthoredSuppression(val relativePath: String, val symbol: String, val rule: String)

  data class CommentPolicyViolation(val lineNumber: Int, val kind: String)

  private val KOTLIN_SOURCE_EXTENSIONS: Set<String> = setOf("kt", "kts")

  fun authoredKotlinSourcesUnder(root: Path): List<Path> {
    if (!Files.isDirectory(root)) {
      error(
        "Missing architecture scan root: ${scanRootLabel(root)}",
      )
    }
    return Files.walk(root).use { paths ->
      paths
        .filter { path ->
          path.isRegularFile() &&
            path.extension in KOTLIN_SOURCE_EXTENSIONS &&
            !isGeneratedOrBuildPath(path)
        }
        .toList()
    }
  }

  private fun scanRootLabel(root: Path): String {
    val normalized = root.toAbsolutePath().normalize()
    return if (normalized.startsWith(runtimeRoot)) {
      runtimeRoot.relativize(normalized).toString().replace('\\', '/')
    } else {
      normalized.toString().replace('\\', '/')
    }
  }

  fun commentAndInterfaceKdocViolations(
    scanRoots: List<String>,
    exemptRelativePaths: Set<String> = emptySet(),
  ): List<String> {
    val violations = mutableListOf<String>()
    scanRoots.forEach { scanRoot ->
      authoredKotlinSourcesUnder(runtimeRoot.resolve(scanRoot)).forEach { sourceFile ->
        val relativePath = runtimeRoot.relativize(sourceFile).toString().replace('\\', '/')
        if (relativePath in exemptRelativePaths) return@forEach
        val source = sourceFile.readText()
        collectCommentPolicyViolations(source).forEach { violation ->
          violations += "$relativePath:${violation.lineNumber}:${violation.kind}"
        }
      }
    }
    return violations.sorted()
  }

  fun lineCommentViolationsIgnoringStringLiterals(source: String): List<Int> =
    collectCommentPolicyViolations(source)
      .filter { violation -> violation.kind == "line-comment" }
      .map { violation -> violation.lineNumber }

  internal fun collectCommentPolicyViolations(source: String): List<CommentPolicyViolation> =
    CommentPolicyScanner(source).scan()

  private class CommentPolicyScanner(private val source: String) {
    private val violations = mutableListOf<CommentPolicyViolation>()
    private var index = 0
    private var line = 1
    private val braceKinds = mutableListOf<BraceKind>()
    private var pendingInterfaceOpen = false

    fun scan(): List<CommentPolicyViolation> {
      while (index < source.length) {
        if (!skipLiteralIfPresent() && !recordCommentIfPresent()) {
          advanceCodeChar()
        }
      }
      return violations
    }

    private fun skipLiteralIfPresent(): Boolean {
      if (source.startsWith("\"\"\"", index)) {
        index = skipTripleQuotedString(source, index + 3)
        return true
      }
      if (source[index] == '"') {
        index = skipQuotedString(source, index + 1)
        return true
      }
      if (source[index] == '\'') {
        index = skipCharLiteral(source, index + 1)
        return true
      }
      return false
    }

    private fun recordCommentIfPresent(): Boolean {
      if (source.startsWith("//", index)) {
        violations += CommentPolicyViolation(line, "line-comment")
        index = skipToEndOfLine(source, index)
        return true
      }
      if (source.startsWith("/**", index)) {
        val start = index
        val end = skipBlockComment(source, index)
        if (!isAllowedKDocSite(source, end, braceKinds.lastOrNull() == BraceKind.INTERFACE)) {
          violations += CommentPolicyViolation(lineAt(start), "kdoc-outside-interface")
        }
        index = end
        return true
      }
      if (source.startsWith("/*", index)) {
        violations += CommentPolicyViolation(line, "block-comment")
        index = skipBlockComment(source, index)
        return true
      }
      return false
    }

    private fun advanceCodeChar() {
      if (interfaceKeywordAt(source, index)) pendingInterfaceOpen = true
      if (pendingInterfaceOpen && nonInterfaceDeclarationKeywordAt(source, index)) {
        pendingInterfaceOpen = false
      }
      when (source[index]) {
        '\n' -> line++
        '{' -> {
          braceKinds += if (pendingInterfaceOpen) BraceKind.INTERFACE else BraceKind.OTHER
          pendingInterfaceOpen = false
        }
        '}' -> {
          if (braceKinds.isNotEmpty()) braceKinds.removeAt(braceKinds.lastIndex)
          pendingInterfaceOpen = false
        }
      }
      index++
    }

    private fun lineAt(charIndex: Int): Int {
      var currentLine = 1
      var scan = 0
      while (scan < charIndex && scan < source.length) {
        if (source[scan] == '\n') currentLine++
        scan++
      }
      return currentLine
    }
  }

  private fun isGeneratedOrBuildPath(path: Path): Boolean =
    path.toString().replace('\\', '/').split('/').any { segment ->
      segment == "build" || segment == "generated"
    }

  private fun interfaceKeywordAt(
    source: String,
    index: Int,
  ): Boolean {
    if (!source.startsWith("interface", index)) return false
    val before = source.getOrNull(index - 1)
    if (before != null && (before.isLetterOrDigit() || before == '_')) return false
    val after = source.getOrNull(index + "interface".length)
    if (after != null && (after.isLetterOrDigit() || after == '_')) return false
    return true
  }

  private fun nonInterfaceDeclarationKeywordAt(
    source: String,
    index: Int,
  ): Boolean =
    listOf("class", "object", "fun", "val", "var", "typealias").any { keyword ->
      source.startsWith(keyword, index) &&
        source.getOrNull(index - 1)?.let { character -> character.isLetterOrDigit() || character == '_' } != true &&
        source.getOrNull(index + keyword.length)
          ?.let { character -> character.isLetterOrDigit() || character == '_' } != true
    }

  private fun isAllowedKDocSite(
    source: String,
    afterCommentIndex: Int,
    insideInterfaceBody: Boolean,
  ): Boolean {
    var remainder = source.substring(afterCommentIndex).trimStart()
    while (remainder.startsWith("@")) {
      val nextLine = remainder.indexOf('\n').takeIf { lineBreak -> lineBreak != -1 } ?: remainder.length
      remainder = remainder.substring(nextLine).trimStart()
    }
    if (INTERFACE_DECLARATION_PREFIX.containsMatchIn(remainder)) return true
    if (!insideInterfaceBody) return false
    return KDOC_INTERFACE_MEMBER_PREFIX.containsMatchIn(remainder)
  }

  private fun skipToEndOfLine(
    source: String,
    from: Int,
  ): Int {
    var index = from
    while (index < source.length && source[index] != '\n') index++
    return index
  }

  private fun skipBlockComment(
    source: String,
    from: Int,
  ): Int {
    val end = source.indexOf("*/", from + 2)
    return if (end == -1) source.length else end + 2
  }

  private fun skipQuotedString(
    source: String,
    from: Int,
  ): Int {
    var index = from
    while (index < source.length) {
      when (source[index]) {
        '\\' -> index += 2
        '"' -> return index + 1
        else -> index++
      }
    }
    return index
  }

  private fun skipTripleQuotedString(
    source: String,
    from: Int,
  ): Int {
    var index = from
    while (index < source.length) {
      if (source.startsWith("\"\"\"", index)) return index + 3
      if (source[index] == '\\') {
        index += 2
        continue
      }
      index++
    }
    return index
  }

  private fun skipCharLiteral(
    source: String,
    from: Int,
  ): Int {
    var index = from
    while (index < source.length) {
      when (source[index]) {
        '\\' -> index += 2
        '\'' -> return index + 1
        else -> index++
      }
    }
    return index
  }

  private enum class BraceKind {
    INTERFACE,
    OTHER,
  }

  private val KDOC_INTERFACE_MEMBER_PREFIX =
    Regex(
      """^(?:(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s+)*)""" +
        """(?:(?:public|internal|private|protected|override|suspend|abstract|open|lateinit|const|data|enum|""" +
        """annotation|value|sealed|external|tailrec|expect|actual|infix|operator|companion)\s+)*""" +
        """(?:fun|val|var|(?:data\s+)?(?:class|object)|interface|typealias)\s+""",
    )
  private val INTERFACE_DECLARATION_PREFIX =
    Regex(
      """^(?:(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^)]*\))?\s+)*)""" +
        """(?:(?:public|internal|private|protected|sealed|fun)\s+)*interface\s+""",
    )
}
