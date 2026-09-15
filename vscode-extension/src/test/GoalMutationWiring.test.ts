import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, it } from "mocha";
import { createGoalMutationRepositories } from "../composition/StatusCompositionRoot";
import { ProcessRunner } from "../infrastructure/cli/ProcessRunner";
import { FakePreferenceCache } from "./fakes/FakePreferenceCache";
import { ScriptedProcessFactory } from "./fakes/ScriptedProcessFactory";

class OverriddenCliPreferenceCache extends FakePreferenceCache {
  constructor(private readonly executable: string) {
    super();
  }

  override getCliExecutableOverride(): string {
    return this.executable;
  }
}

describe("GoalMutationWiring", () => {
  it("the composed pause and stop repositories each issue their own verb", async () => {
    const projectRoot = fs.mkdtempSync(path.join(os.tmpdir(), "mutation-wiring-"));
    const executable = path.join(projectRoot, "skill-bill");
    fs.writeFileSync(executable, "");
    fs.chmodSync(executable, 0o755);
    const pauseFactory = new ScriptedProcessFactory(0);
    const stopFactory = new ScriptedProcessFactory(0);

    const mutations = createGoalMutationRepositories(
      new OverriddenCliPreferenceCache(executable),
      new ProcessRunner(pauseFactory),
      new ProcessRunner(stopFactory),
    );

    await mutations.pause.requestMutation(projectRoot, "SKILL-238");
    await mutations.stop.requestMutation(projectRoot, "SKILL-238");

    assert.deepEqual(pauseFactory.commands[0]?.slice(0, 4), [executable, "goal", "pause", "SKILL-238"]);
    assert.deepEqual(stopFactory.commands[0]?.slice(0, 4), [executable, "goal", "stop", "SKILL-238"]);
  });
});
