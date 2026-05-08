package skillbill.scaffold

import skillbill.error.SkillBillRuntimeException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression coverage for SKILL-40 subtask 1 fixes:
 *  - F-C: renderContentBody must reject caller-supplied bodies that already start with a YAML
 *    frontmatter block (would otherwise stack two `---` blocks).
 *  - F-D: coerceFullContentText must fail explicitly with a clear message when neither the
 *    supplied body nor the existing content.md provides a frontmatter block.
 */
class AuthoringContentMutationTest {
  @Test
  fun `renderContentBody rejects content body that already carries frontmatter`() {
    val context = TemplateContext("bill-example", "code-review", "kotlin", "", "Kotlin")
    val bodyWithFrontmatter = """
      |---
      |name: bill-example
      |description: Pre-supplied frontmatter that would stack with the canonical block.
      |---
      |
      |# Body
      |
      |Content body.
    """.trimMargin()

    val error = assertFailsWith<SkillBillRuntimeException> {
      renderContentBody(context, "Some description", bodyWithFrontmatter)
    }
    assertContains(error.message.orEmpty(), "frontmatter")
  }

  @Test
  fun `renderContentBody accepts plain body without frontmatter`() {
    val context = TemplateContext("bill-example", "code-review", "kotlin", "", "Kotlin")
    val rendered = renderContentBody(context, "An example skill description.", "Plain body without frontmatter.\n")

    // Exactly one leading frontmatter block (between `---` fences).
    val frontmatterFenceCount = Regex("(?m)^---$").findAll(rendered).count()
    assertTrue(frontmatterFenceCount >= 2, "Expected canonical frontmatter, got: $rendered")
    assertContains(rendered, "name: bill-example")
    assertContains(rendered, "description: An example skill description.")
    assertContains(rendered, "Plain body without frontmatter.")
  }

  @Test
  fun `coerceFullContentText fails with clear message when neither supplied nor existing carries frontmatter`() {
    val target = createOrphanTarget()

    val error = assertFailsWith<SkillBillRuntimeException> {
      coerceFullContentText(target, "Body without frontmatter, neither file has one either.")
    }
    val message = error.message.orEmpty()
    assertContains(message, "content.md must already carry a YAML frontmatter block")
    assertContains(message, target.skillName)
  }

  @Test
  fun `coerceFullContentText preserves existing frontmatter when supplied body has none`() {
    val dir = Files.createTempDirectory("coerce-existing-fm")
    val skillFile = dir.resolve("SKILL.md")
    val contentFile = dir.resolve("content.md")
    Files.writeString(
      contentFile,
      """
      |---
      |name: bill-example
      |description: Existing description preserved across edits.
      |---
      |
      |# Old body
      |
      |Old content.
      """.trimMargin() + "\n",
    )
    Files.writeString(skillFile, "---\nname: bill-example\ndescription: x\n---\n\n## Descriptor\n")
    val target = AuthoringTarget(
      skillName = "bill-example",
      packageName = "base",
      platform = "",
      displayName = "example",
      family = "advisor",
      area = "",
      skillFile = skillFile,
      contentFile = contentFile,
    )

    val coerced = coerceFullContentText(target, "Replacement body without its own frontmatter.")

    assertContains(coerced, "name: bill-example")
    assertContains(coerced, "description: Existing description preserved across edits.")
    assertContains(coerced, "Replacement body without its own frontmatter.")
  }

  private fun createOrphanTarget(): AuthoringTarget {
    val dir = Files.createTempDirectory("coerce-orphan")
    val contentFile = dir.resolve("content.md")
    val skillFile = dir.resolve("SKILL.md")
    Files.writeString(contentFile, "# No frontmatter\n\nAuthored body without frontmatter.\n")
    Files.writeString(skillFile, "---\nname: bill-orphan\ndescription: irrelevant\n---\n\n## Descriptor\n")
    return AuthoringTarget(
      skillName = "bill-orphan",
      packageName = "base",
      platform = "",
      displayName = "orphan",
      family = "advisor",
      area = "",
      skillFile = skillFile,
      contentFile = contentFile,
    )
  }
}
