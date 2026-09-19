package skillbill.infrastructure.workflow.git.process
import skillbill.infrastructure.workflow.feature.error
import skillbill.infrastructure.workflow.featuretask.error
import skillbill.infrastructure.workflow.git.checkpoint.git
import skillbill.infrastructure.workflow.git.goal.error
import skillbill.infrastructure.workflow.git.goal.value
import skillbill.infrastructure.workflow.git.local.git
import skillbill.infrastructure.workflow.git.protected.git
import skillbill.infrastructure.workflow.git.protected.process
import skillbill.infrastructure.workflow.git.repository.git
import skillbill.infrastructure.workflow.git.repository.process
import skillbill.infrastructure.workflow.git.scoped.git
import skillbill.infrastructure.workflow.git.suppression.git
import skillbill.infrastructure.workflow.git.suppression.process
import skillbill.infrastructure.workflow.git.workflow.error
import skillbill.infrastructure.workflow.git.workflow.git
import skillbill.infrastructure.workflow.git.workflow.process
import skillbill.infrastructure.workflow.git.workflow.value
import skillbill.infrastructure.workflow.process.GIT_HOOKED_COMMAND_TIMEOUT_SECONDS
import skillbill.infrastructure.workflow.process.GIT_TIMEOUT_SECONDS
import skillbill.infrastructure.workflow.process.gitTimeoutSeconds
import skillbill.infrastructure.workflow.process.withValue
import skillbill.infrastructure.workflow.review.broker.error
import skillbill.ports.workflow.gitops.model.WorkflowGitOperationResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitProcessSupportTest {
  @Test
  fun `commit and push wait long enough for a pre-commit hook`() {
    assertEquals(GIT_TIMEOUT_SECONDS, gitTimeoutSeconds(listOf("status", "--porcelain")))
    assertEquals(GIT_TIMEOUT_SECONDS, gitTimeoutSeconds(listOf("rev-parse", "HEAD")))
    assertEquals(GIT_HOOKED_COMMAND_TIMEOUT_SECONDS, gitTimeoutSeconds(listOf("commit", "-m", "msg")))
    assertEquals(GIT_HOOKED_COMMAND_TIMEOUT_SECONDS, gitTimeoutSeconds(listOf("commit", "--amend", "--no-edit")))
    assertEquals(GIT_HOOKED_COMMAND_TIMEOUT_SECONDS, gitTimeoutSeconds(listOf("push", "-u", "origin", "feat/x")))
    assertTrue(GIT_HOOKED_COMMAND_TIMEOUT_SECONDS > GIT_TIMEOUT_SECONDS)
  }

  @Test
  fun `withValue changes only successful results`() {
    val failed = WorkflowGitOperationResult.Failed(error = "failure", value = "diagnostic")

    assertEquals(failed, failed.withValue("replacement"))
    assertEquals("replacement", WorkflowGitOperationResult.Ok("original").withValue("replacement").value)
  }
}
