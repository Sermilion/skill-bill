import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, it } from "mocha";
import { CliExecutableSource } from "../infrastructure/cli/CliExecutableResolver";
import {
  CliGoalMutationRepository,
  GOAL_PAUSE_MUTATION,
  GOAL_STOP_MUTATION,
} from "../infrastructure/cli/CliGoalMutationRepository";
import { ProcessRunner } from "../infrastructure/cli/ProcessRunner";
import { ScriptedProcessFactory } from "./fakes/ScriptedProcessFactory";
import { FakePreferenceCache } from "./fakes/FakePreferenceCache";

describe("CliGoalMutationRepository", () => {
  const prefs = new FakePreferenceCache();
  const executable = "/usr/bin/skill-bill";

  it("stop and pause build expected argv on distinct runners", async () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "goal-mutation-"));
    const canonical = fs.realpathSync(root);

    const pauseFactory = new ScriptedProcessFactory(0);
    const pauseRunner = new ProcessRunner(pauseFactory);
    const pauseRepo = new CliGoalMutationRepository(GOAL_PAUSE_MUTATION, prefs, pauseRunner, () => ({
      kind: "found" as const,
      path: executable,
      source: CliExecutableSource.SEARCH_PATH,
    }));
    const pauseOutcome = await pauseRepo.requestMutation(path.join(root, "..", path.basename(root)), "SKILL-168");
    assert.equal(pauseOutcome.kind, "requested");
    assert.deepEqual(pauseFactory.commands[0], [
      executable,
      "goal",
      "pause",
      "SKILL-168",
      "--repo-root",
      canonical,
    ]);

    const stopFactory = new ScriptedProcessFactory(0);
    const stopRunner = new ProcessRunner(stopFactory);
    const stopRepo = new CliGoalMutationRepository(GOAL_STOP_MUTATION, prefs, stopRunner, () => ({
      kind: "found" as const,
      path: executable,
      source: CliExecutableSource.SEARCH_PATH,
    }));
    const stopOutcome = await stopRepo.requestMutation(root, "SKILL-168");
    assert.equal(stopOutcome.kind, "requested");
    assert.deepEqual(stopFactory.commands[0], [
      executable,
      "goal",
      "stop",
      "SKILL-168",
      "--repo-root",
      canonical,
    ]);

    assert.notEqual(pauseRunner, stopRunner);
  });

  it("failure summaries omit stdout stderr and paths", async () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "goal-mutation-leak-"));
    const canonical = fs.realpathSync(root);
    const secretStdout = "SECRET_STDOUT_MARKER";
    const summaries: string[] = [];

    const failing = new CliGoalMutationRepository(
      GOAL_PAUSE_MUTATION,
      prefs,
      new ProcessRunner(new ScriptedProcessFactory(3, secretStdout)),
      () => ({ kind: "found" as const, path: executable, source: CliExecutableSource.SEARCH_PATH }),
    );
    const pauseFailed = await failing.requestMutation(root, "SKILL-168");
    if (pauseFailed.kind === "failed") {
      summaries.push(pauseFailed.summary);
    }

    const stopFailed = await new CliGoalMutationRepository(
      GOAL_STOP_MUTATION,
      prefs,
      new ProcessRunner(new ScriptedProcessFactory(3, secretStdout)),
      () => ({ kind: "found" as const, path: executable, source: CliExecutableSource.SEARCH_PATH }),
    ).requestMutation(root, "SKILL-168");
    if (stopFailed.kind === "failed") {
      summaries.push(stopFailed.summary);
    }

    assert.ok(summaries.length >= 2);
    for (const summary of summaries) {
      assert.ok(summary.length > 0);
      assert.ok(summary.length <= 120);
      assert.ok(!summary.includes(secretStdout));
      assert.ok(!summary.includes(canonical));
      assert.ok(!summary.includes("/"));
    }
  });
});
