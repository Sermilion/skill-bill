package skillbill.desktop.feature.skillbill.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import skillbill.desktop.core.designsystem.SkillBillDarkThemeTokens
import skillbill.desktop.core.designsystem.SkillBillLightThemeTokens
import skillbill.desktop.core.designsystem.SkillBillMaterialTheme

@OptIn(ExperimentalTestApi::class)
class SkillBillFrameTokenWiringTest {

  @Test
  fun `diff line classification maps unified diff prefixes to feature UI tokens`() {
    val tokens = SkillBillDarkThemeTokens.diff

    assertEquals(tokens.metadata, diffColorForLine("+++ b/file.kt", tokens))
    assertEquals(tokens.metadata, diffColorForLine("--- a/file.kt", tokens))
    assertEquals(tokens.hunk, diffColorForLine("@@ -1 +1 @@", tokens))
    assertEquals(tokens.addition, diffColorForLine("+added", tokens))
    assertEquals(tokens.deletion, diffColorForLine("-removed", tokens))
    assertEquals(tokens.context, diffColorForLine(" unchanged", tokens))
  }

  @Test
  fun `code pane colors pair light tokens with light pane background`() = runComposeUiTest {
    var colors: CodePaneColors? = null

    setContent {
      SkillBillMaterialTheme(darkTheme = false) {
        colors = codePaneColors()
      }
    }

    waitForIdle()

    val paneColors = colors ?: error("Code pane colors were not captured.")
    assertEquals(SkillBillLightThemeTokens.diff, paneColors.diff)
    assertReadable(paneColors.editorText, paneColors.background)
    assertReadable(paneColors.yamlFallback, paneColors.background)
    assertReadable(paneColors.diff.context, paneColors.background)
    assertReadable(paneColors.diff.addition, paneColors.background)
    assertReadable(paneColors.diff.deletion, paneColors.background)
    assertReadable(paneColors.diff.hunk, paneColors.background)
    assertReadable(paneColors.diff.metadata, paneColors.background)
    assertReadableNonText(paneColors.editorCursor, paneColors.background)
  }

  @Test
  fun `code pane colors pair dark tokens with dark pane background`() = runComposeUiTest {
    var colors: CodePaneColors? = null

    setContent {
      SkillBillMaterialTheme(darkTheme = true) {
        colors = codePaneColors()
      }
    }

    waitForIdle()

    val paneColors = colors ?: error("Code pane colors were not captured.")
    assertEquals(SkillBillDarkThemeTokens.diff, paneColors.diff)
    assertReadable(paneColors.editorText, paneColors.background)
    assertReadable(paneColors.yamlFallback, paneColors.background)
    assertReadable(paneColors.diff.context, paneColors.background)
    assertReadable(paneColors.diff.addition, paneColors.background)
    assertReadable(paneColors.diff.deletion, paneColors.background)
    assertReadable(paneColors.diff.hunk, paneColors.background)
    assertReadable(paneColors.diff.metadata, paneColors.background)
    assertReadableNonText(paneColors.editorCursor, paneColors.background)
  }

  @Test
  fun `diff parsing stays out of core design-system tokens`() {
    val sourcePath = sourceFile(
      "core/designsystem/src/commonMain/kotlin/skillbill/desktop/core/designsystem/SkillBillTokens.kt",
      "runtime-kotlin/runtime-desktop/core/designsystem/src/commonMain/kotlin/skillbill/desktop/core/designsystem/SkillBillTokens.kt",
    )
    val source = Files.readString(sourcePath)

    assertFalse(source.contains("colorForLine"))
    assertFalse(source.contains("startsWith(\"@@\")"))
  }

  private fun assertReadable(foreground: Color, background: Color) {
    val ratio = contrastRatio(foreground, background)
    assertTrue(ratio >= MINIMUM_BODY_TEXT_CONTRAST, "Expected contrast >= $MINIMUM_BODY_TEXT_CONTRAST, got $ratio")
  }

  private fun assertReadableNonText(foreground: Color, background: Color) {
    val ratio = contrastRatio(foreground, background)
    assertTrue(ratio >= MINIMUM_NON_TEXT_CONTRAST, "Expected contrast >= $MINIMUM_NON_TEXT_CONTRAST, got $ratio")
  }

  private fun contrastRatio(foreground: Color, background: Color): Double {
    val effectiveForeground = foreground.compositeOver(background)
    val lighter = maxOf(effectiveForeground.relativeLuminance(), background.relativeLuminance())
    val darker = minOf(effectiveForeground.relativeLuminance(), background.relativeLuminance())
    return (lighter + 0.05) / (darker + 0.05)
  }

  private fun Color.compositeOver(background: Color): Color {
    if (alpha >= 1f) return this
    val inverseAlpha = 1f - alpha
    return Color(
      red = red * alpha + background.red * inverseAlpha,
      green = green * alpha + background.green * inverseAlpha,
      blue = blue * alpha + background.blue * inverseAlpha,
      alpha = 1f,
    )
  }

  private fun Color.relativeLuminance(): Double {
    fun channel(value: Float): Double {
      val normalized = value.toDouble()
      return if (normalized <= 0.03928) {
        normalized / 12.92
      } else {
        ((normalized + 0.055) / 1.055).pow(2.4)
      }
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
  }

  private fun sourceFile(vararg relativePaths: String): Path {
    var current = Path.of("").toAbsolutePath()
    while (true) {
      relativePaths.forEach { relativePath ->
        val candidate = current.resolve(relativePath)
        if (Files.exists(candidate)) return candidate
      }
      current = current.parent ?: error(
        "Could not locate ${relativePaths.toList()} from ${Path.of("").toAbsolutePath()}",
      )
    }
  }

  private companion object {
    const val MINIMUM_BODY_TEXT_CONTRAST = 4.5
    const val MINIMUM_NON_TEXT_CONTRAST = 3.0
  }
}
