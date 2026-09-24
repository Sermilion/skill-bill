package skillbill.infrastructure.contracts.workflow.issue

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import skillbill.contracts.issuekey.MAX_ISSUE_KEY_LENGTH
import skillbill.testing.repoRootFromTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class IssueKeySchemaLengthRepoTest {
  @Test
  fun `the canonical issue-key schema pins the Kotlin issue-key bounds`() {
    val schema =
      YAMLMapper().readTree(
        Files.readString(repoRootFromTest().resolve("orchestration/contracts/issue-key-schema.yaml")),
      )

    assertEquals(1, schema.path("minLength").asInt(), "issue-key schema minLength must be 1")
    assertEquals(
      MAX_ISSUE_KEY_LENGTH,
      schema.path("maxLength").asInt(),
      "issue-key schema maxLength drifted from MAX_ISSUE_KEY_LENGTH",
    )
  }
}
