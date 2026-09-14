# SKILL-244: Runtime start script leaks its jlink JAVA_HOME into pack gate commands

## Intended Outcome

A pack validation gate that shells out to a build tool must not inherit the Skill
Bill runtime's own trimmed jlink image as `JAVA_HOME`. Today every child process
the runtime spawns inherits `JAVA_HOME=<runtime image>`, so the Kotlin pack's
`./gradlew compileKotlin` gate cannot start a Gradle daemon and the subtask
blocks with a finding no edit to the branch can clear.

Observed on 2026-09-14 during goal `SKILL-243`, subtask 1, workflow
`wftr-20260914-121722-8pbp`: build blocked after 3 repair turns with 1 finding.

## Defect Analysis

`StartScriptJavaGuard` patches the generated start script so the runtime runs on
its bundled JRE. The installed script
`~/.skill-bill/runtime/runtime-cli/bin/runtime-cli` therefore contains:

```sh
JAVA_HOME="$APP_HOME"     # line 69
JAVA_HOME="$APP_HOME"     # line 97
```

`$APP_HOME` is `~/.skill-bill/runtime/runtime-cli`, a jlink runtime image, not a
JDK. The export is inherited by every process the runtime launches, including the
agent session that runs a pack `validation_gate` command.

`platform-packs/kotlin/platform.yaml:110-116` declares:

```yaml
build_command: [./gradlew, compileKotlin]
cache_bypassing_build_command: [./gradlew, compileKotlin, --no-build-cache]
```

`runtime-kotlin/gradlew` is the stock wrapper and uses `$JAVA_HOME/bin/java`
verbatim. Gradle forks its daemon with
`-javaagent:gradle-instrumentation-agent-9.3.0.jar`, which requires the
`java.instrument` module. The jlink image does not ship it, so the daemon dies
before any Kotlin is compiled:

```
Unable to start the daemon process.
Process command line: /Users/<user>/.skill-bill/runtime/runtime-cli/bin/java ...
Error occurred during initialization of VM
Could not find agent library instrument ... Module java.instrument may be missing from runtime image.
```

The same command run from an operator shell whose `JAVA_HOME` is a real JDK 21
succeeds, both cached and with `--no-build-cache`. The failure is entirely in the
inherited environment.

The repo already owns the correct resolution logic:
`runtime-kotlin/build-logic/convention/src/main/resources/skill-bill-java-guard.sh`
honours `SKILL_BILL_JAVA_HOME`, falls back to `JAVA_HOME` when it passes
`skill_bill_java_home_ok`, unsets it otherwise, and scans for a qualifying JDK.
`install.sh:51-64` and `uninstall.sh:29-42` source it. Nothing on the gate path
does.

**Why it presents as an unclearable finding.** The build gate is agent-run and
the agent may only run the pack's `build_command`. A machine-specific
`org.gradle.java.home` in a committed `gradle.properties` would break every other
contributor, and sanitising the child environment in the checkout has no effect
because the gate is executed by the *installed* runtime. So the agent correctly
reports a finding it cannot fix, exhausts its repair turns, and blocks.

## Acceptance Criteria

1. A pack validation-gate command launched by the runtime does not inherit
   `JAVA_HOME` pointing at the runtime's own jlink image.
2. The Kotlin pack's `build_command` and `cache_bypassing_build_command` start a
   Gradle daemon and compile on a host whose only qualifying JDK is outside the
   runtime image.
3. `SKILL_BILL_JAVA_HOME`, when set, selects the JVM used for gate commands, with
   the same acceptance rule as `skill-bill-java-guard.sh`.
4. When no qualifying JDK can be resolved, the gate fails loudly naming the
   resolved candidate and the required major version, rather than surfacing a
   daemon-start error as a build finding.
5. The runtime still runs itself on its bundled JRE; fixing the leak does not
   reintroduce a JDK requirement for running Skill Bill.

## Constraints

- No machine-specific paths in any committed file.
- Do not require operators to edit `~/.gradle/gradle.properties`; that is
  machine-wide and affects unrelated projects on the same host.
- Reuse `skill-bill-java-guard.sh`; do not add a second resolution rule.

## Non-Goals

- Changing the pack gate command vocabulary or the agent-run build contract.
- Bundling a full JDK in the runtime image.
- Reworking `StartScriptJavaGuard`'s purpose of pinning the runtime's own JVM.

## Validation Strategy

- A test asserts the environment handed to a pack gate command carries no
  `JAVA_HOME` inside the runtime image.
- A regression test reproduces the `wftr-20260914-121722-8pbp` shape: a gate
  command run with the runtime image as `JAVA_HOME` must resolve a qualifying JDK
  or fail with the typed error from criterion 4, never a daemon-start finding.

## Decomposition

One subtask. The leak, the resolution reuse, and the loud-fail path are one
commit: shipping the guard into the runtime without consuming it, or consuming a
guard the installed runtime does not carry, leaves a tree that does not build the
Kotlin pack gate.

1. `spec_subtask_1_resolve_gate_jvm.md` — resolve the gate JVM through
   `skill-bill-java-guard.sh` and stop leaking the jlink image into child
   processes.
