package skillbill.desktop.core.designsystem

import androidx.compose.ui.graphics.Color

typealias SkillBillColor = Color

data class SkillBillTextFieldTokens(
  val text: Color,
  val disabledText: Color,
  val placeholder: Color,
  val disabledPlaceholder: Color,
  val container: Color,
  val disabledContainer: Color,
  val border: Color,
  val focusedBorder: Color,
  val disabledBorder: Color,
  val cursor: Color,
)

data class SkillBillSurfaceTone(
  val container: Color,
  val content: Color,
  val border: Color,
)

data class SkillBillSemanticToneTokens(
  val dialog: SkillBillSurfaceTone,
  val scrim: Color,
  val warningBanner: SkillBillSurfaceTone,
  val successBanner: SkillBillSurfaceTone,
  val errorBanner: SkillBillSurfaceTone,
)

data class YamlSyntaxColors(
  val comment: Color,
  val key: Color,
  val string: Color,
  val marker: Color,
  val scalar: Color,
)

data class SkillBillSyntaxTokens(
  val yaml: YamlSyntaxColors,
)

data class SkillBillDiffTokens(
  val metadata: Color,
  val hunk: Color,
  val addition: Color,
  val deletion: Color,
  val context: Color,
)

data class SkillBillThemeTokens(
  val textField: SkillBillTextFieldTokens,
  val semanticTones: SkillBillSemanticToneTokens,
  val syntax: SkillBillSyntaxTokens,
  val diff: SkillBillDiffTokens,
)
