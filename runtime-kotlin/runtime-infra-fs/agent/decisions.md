# runtime-infra-fs decisions

## SKILL-353 legitimate-absence fallbacks

- `SkillContentIdentity.fromSource` uses the authored path string when `toRealPath()` fails because identity must remain stable across environments where real-path resolution is blocked while the authored tree is still readable.
- `FileSystemCheckedOutBranchSource` returns null when `.git/HEAD` or a gitdir pointer cannot be read; that is the expected signal for a non-git or incomplete checkout rather than a typed failure.
- `GovernedSkillDriftReport.driftDisplayPath` falls back to an absolute path when the reported path is outside the repository root so operators still see a locator for pack-owned pointers.
- `processBootIdentity` uses `boot-identity-unavailable` when neither the kernel boot id nor init-process birth is readable; process-birth evidence remains the decisive reused-pid guard, and the diagnostics port records the absence.
- `AgentRunAdapters` drops malformed intermediate JSONL events while retaining the terminal result or bounded raw preview; malformed lines are not user-facing evidence and the decoder preserves the undecodable stream when no terminal event exists.
- `NativeAgentLinkInventoryDecode.isSemanticallyValid` returns false for an unreadable or mismatched managed link so reconciliation removes it rather than retaining an unverifiable entry; `InstallPrimitives` treats an absent or non-symbolic link target as no existing link to reconcile.
