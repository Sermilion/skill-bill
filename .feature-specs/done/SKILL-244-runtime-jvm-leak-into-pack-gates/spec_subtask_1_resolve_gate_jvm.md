# SKILL-244 Subtask 1: Resolve the gate JVM through the shared Java guard

## Scope

Stop the installed runtime from handing its own jlink image to child processes as
`JAVA_HOME`, and resolve the JVM those processes see through the existing
`skill-bill-java-guard.sh` rule.

In scope:

- Ship `skill-bill-java-guard.sh` with the installed runtime so the resolution
  rule is available at gate time, not only at `../../../install.sh` / `uninstall.sh` time.
- Resolve the child-process `JAVA_HOME` by evaluating that guard (the same way
  `install.sh:51-64` sources it): `SKILL_BILL_JAVA_HOME` first, then an inherited
  `JAVA_HOME` that passes `skill_bill_java_home_ok`, then a qualifying PATH
  `java` with `JAVA_HOME` unset, then a scan.
- Apply the resolved value on both surfaces that launch a pack gate command:
  the runtime-run gate in
  `../../../runtime-kotlin/runtime-infra-fs/src/main/kotlin/skillbill/infrastructure/fs/validation/FileSystemValidationGateRunner.kt`,
  which inherits the parent environment wholesale, and the agent-run build gate
  environment built in
  `runtime-kotlin/runtime-infra-fs/src/main/kotlin/skillbill/infrastructure/fs/launcher/process/JvmAgentRunProcessLaunchEnvironment.kt`,
  where `inheritEnvironment = true` passes the leaked value straight through.
- Raise a typed error when no qualifying JDK resolves, naming the rejected
  candidate and the required major version, so the failure does not reach the
  agent as a build finding.
- Keep the runtime's own launcher pinned to the bundled JRE: `StartScriptJavaGuard`
  keeps setting `JAVA_HOME="$APP_HOME"` for the runtime process itself.

## Acceptance Criteria

1. The environment handed to a pack validation-gate command carries no
   `JAVA_HOME` that points inside the runtime's jlink image, on both the
   runtime-run gate path and the agent-run gate path.
2. The Kotlin pack's `build_command` and `cache_bypassing_build_command` start a
   Gradle daemon and compile when the launching runtime process itself has the
   jlink image as `JAVA_HOME` and the only qualifying JDK is outside that image.
3. `SKILL_BILL_JAVA_HOME`, when set and accepted by `skill_bill_java_home_ok`,
   selects the JVM used for gate commands; when set and rejected, resolution
   falls through the guard's remaining branches rather than using it.
4. When no qualifying JDK resolves, the gate fails with a typed error naming the
   resolved candidate and the required major version, and no `ValidationGateFinding`
   is produced for that condition.
5. The runtime still launches on its bundled JRE with no JDK installed for the
   runtime process itself.
6. Resolution has exactly one rule: no second acceptance or scan implementation
   is added alongside `skill-bill-java-guard.sh`.
7. No committed file contains a machine-specific JDK path, and no change requires
   an operator edit to `~/.gradle/gradle.properties`.

## Non-Goals

- Changing the pack gate command vocabulary or the agent-run build contract.
- Bundling a full JDK in the runtime image.
- Reworking `StartScriptJavaGuard`'s purpose of pinning the runtime's own JVM.
- Changing `ISOLATED_LAUNCH_PASSTHROUGH_KEYS` semantics for lanes that already
  launch with `inheritEnvironment = false`, beyond what criterion 1 requires.

## Dependency Notes

None. This is the only subtask; it depends on no prior work and nothing depends
on it.

Touches install staging (the guard must reach `~/.skill-bill/runtime/`), so
`./install.sh` must be rerun before the fix is observable end to end.

## Validation Strategy

- A test asserts the environment map handed to a pack gate command contains no
  `JAVA_HOME` under the runtime image root, given a parent environment that has
  one.
- A regression test in the `wftr-20260914-121722-8pbp` shape: a gate command
  launched with the runtime image as the inherited `JAVA_HOME` either resolves a
  qualifying JDK or raises the typed error from criterion 4 — never a
  daemon-start finding.
- A test covers `SKILL_BILL_JAVA_HOME` precedence and rejection of a candidate
  that fails the guard's acceptance rule.
- Pack gate proof: `./gradlew compileKotlin` under the Kotlin pack's
  `validation_gate` starts its daemon.

## Next Path

Single subtask; on completion the goal finalises.
