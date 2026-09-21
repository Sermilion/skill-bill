package skillbill.infrastructure.workflow.git.worktree

import skillbill.infrastructure.workflow.git.workflow.git
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeAddRequest
import skillbill.ports.workflow.gitops.worktree.LinkedWorktreeRemoveRequest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GitLinkedWorktreeOperationsTest {
  @Test
  fun `control and treatment worktrees have independent writable files at the same revision`() {
    val repository = Files.createTempDirectory("experiment-worktree-repository")
    git(repository, "init", "-b", "main")
    git(repository, "config", "user.email", "test@example.invalid")
    git(repository, "config", "user.name", "Skill Bill Test")
    Files.writeString(repository.resolve("README.md"), "base\n")
    git(repository, "add", "README.md")
    git(repository, "commit", "-m", "base")
    val base = git(repository, "rev-parse", "HEAD")
    val control = repository.parent.resolve("${repository.fileName}-control")
    val treatment = repository.parent.resolve("${repository.fileName}-treatment")

    GitLinkedWorktreeOperations.addLinkedWorktree(
      LinkedWorktreeAddRequest(repository, control, "experiment/control", base),
    )
    GitLinkedWorktreeOperations.addLinkedWorktree(
      LinkedWorktreeAddRequest(repository, treatment, "experiment/treatment", base),
    )
    try {
      Files.writeString(control.resolve("control-only.txt"), "control\n")
      assertFalse(Files.exists(treatment.resolve("control-only.txt")))
      assertEquals(base, git(control, "rev-parse", "HEAD"))
      assertEquals(base, git(treatment, "rev-parse", "HEAD"))
    } finally {
      GitLinkedWorktreeOperations.removeLinkedWorktree(LinkedWorktreeRemoveRequest(repository, control))
      GitLinkedWorktreeOperations.removeLinkedWorktree(LinkedWorktreeRemoveRequest(repository, treatment))
    }
  }
}
