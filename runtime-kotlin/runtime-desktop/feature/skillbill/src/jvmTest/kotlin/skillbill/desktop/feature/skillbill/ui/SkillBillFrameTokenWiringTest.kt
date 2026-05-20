package skillbill.desktop.feature.skillbill.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import skillbill.desktop.core.designsystem.SkillBillColor
import skillbill.desktop.core.designsystem.SkillBillDarkThemeTokens
import skillbill.desktop.core.designsystem.SkillBillLightThemeTokens
import skillbill.desktop.core.designsystem.SkillBillMaterialTheme
import skillbill.desktop.core.designsystem.SkillBillOnYellow
import skillbill.desktop.core.designsystem.SkillBillTheme
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

@OptIn(ExperimentalTestApi::class)
class SkillBillFrameTokenWiringTest {

  @Test
  fun `diff line classification maps unified diff prefixes to feature UI tokens`() {
    assertEquals(DiffLineRole.Metadata, diffRoleForLine("+++ b/file.kt"))
    assertEquals(DiffLineRole.Metadata, diffRoleForLine("--- a/file.kt"))
    assertEquals(DiffLineRole.Hunk, diffRoleForLine("@@ -1 +1 @@"))
    assertEquals(DiffLineRole.Addition, diffRoleForLine("+added"))
    assertEquals(DiffLineRole.Deletion, diffRoleForLine("-removed"))
    assertEquals(DiffLineRole.Context, diffRoleForLine(" unchanged"))
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
    assertEquals(SkillBillLightThemeTokens.syntax.yaml, paneColors.yaml)
    assertEquals(SkillBillLightThemeTokens.syntax.yaml.scalar, paneColors.yamlFallback)
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
    assertEquals(SkillBillDarkThemeTokens.syntax.yaml, paneColors.yaml)
    assertEquals(SkillBillDarkThemeTokens.syntax.yaml.scalar, paneColors.yamlFallback)
  }

  @Test
  fun `workspace yellow controls do not inherit light material onPrimary`() = runComposeUiTest {
    var materialOnPrimary: SkillBillColor? = null
    var workspaceForeground: SkillBillColor? = null

    setContent {
      SkillBillMaterialTheme(darkTheme = false) {
        materialOnPrimary = SkillBillTheme.colors.onPrimary
        workspaceForeground = workspacePrimaryControlForeground()
      }
    }

    waitForIdle()

    assertEquals(SkillBillOnYellow, workspaceForeground)
    assertNotEquals(materialOnPrimary, workspaceForeground)
  }

  @Test
  fun `diff parsing stays out of core design-system tokens`() {
    val sourcePath = repoRootFromTest()
      .resolve("runtime-kotlin/runtime-desktop/core/designsystem/src/commonMain/kotlin")
      .resolve("skillbill/desktop/core/designsystem/SkillBillTokens.kt")
    val source = Files.readString(sourcePath)

    assertFalse(source.contains("colorForLine"))
    assertFalse(source.contains("startsWith(\"@@\")"))
  }
}
