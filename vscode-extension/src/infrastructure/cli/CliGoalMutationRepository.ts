import * as fs from "fs";
import * as path from "path";
import { GoalMutationOutcome, GoalMutationRepository } from "../../application/GoalMutationRepository";
import { PreferenceCachePort } from "../../application/PreferenceCachePort";
import {
  DEFAULT_CLI_TIMEOUT_MS,
  DEFAULT_STDERR_LIMIT_BYTES,
  DEFAULT_STDOUT_LIMIT_BYTES,
  GOAL_PAUSE_VERB,
  GOAL_STOP_VERB,
  REPO_ROOT_OPTION,
} from "../../domain/Constants";
import { CliExecutableResolution, resolveCliExecutable } from "./CliExecutableResolver";
import { BoundedProcessResult, ProcessRunner, ProcessSpec } from "./ProcessRunner";

export interface GoalMutation {
  readonly verb: readonly string[];
  readonly blankKeySummary: string;
  readonly launchFailureSummary: string;
  readonly cancelledSummary: string;
  readonly timedOutSummary: string;
  readonly declinedSummary: string;
}

export const GOAL_PAUSE_MUTATION: GoalMutation = {
  verb: GOAL_PAUSE_VERB,
  blankKeySummary: "No issue key to pause",
  launchFailureSummary: "Pause request failed to start",
  cancelledSummary: "Pause request cancelled",
  timedOutSummary: "Pause request timed out",
  declinedSummary: "Skill Bill declined the pause request",
};

export const GOAL_STOP_MUTATION: GoalMutation = {
  verb: GOAL_STOP_VERB,
  blankKeySummary: "No issue key to stop",
  launchFailureSummary: "Stop request failed to start",
  cancelledSummary: "Stop request cancelled",
  timedOutSummary: "Stop request timed out",
  declinedSummary: "Skill Bill declined the stop request",
};

export class CliGoalMutationRepository implements GoalMutationRepository {
  constructor(
    private readonly mutation: GoalMutation,
    private readonly preferences: PreferenceCachePort,
    private readonly processRunner: ProcessRunner,
    private readonly executableResolver: () => CliExecutableResolution = () =>
      resolveCliExecutable(this.preferences),
    private readonly timeoutMs: number = DEFAULT_CLI_TIMEOUT_MS,
    private readonly stdoutLimitBytes: number = DEFAULT_STDOUT_LIMIT_BYTES,
    private readonly stderrLimitBytes: number = DEFAULT_STDERR_LIMIT_BYTES,
  ) {}

  async requestMutation(projectRoot: string, issueKey: string): Promise<GoalMutationOutcome> {
    const key = issueKey.trim();
    if (!key) {
      return { kind: "failed", summary: this.mutation.blankKeySummary };
    }
    const resolution = this.executableResolver();
    if (resolution.kind === "missing") {
      return { kind: "failed", summary: "Skill Bill CLI executable not found" };
    }
    if (resolution.kind === "misconfigured") {
      return { kind: "failed", summary: "Skill Bill CLI executable override is not usable" };
    }

    let canonicalRoot: string;
    try {
      canonicalRoot = fs.realpathSync(path.resolve(projectRoot));
    } catch {
      return { kind: "failed", summary: "Project root is not a usable path" };
    }

    let result: BoundedProcessResult;
    try {
      const spec: ProcessSpec = {
        command: [resolution.path, ...this.mutation.verb, key, REPO_ROOT_OPTION, canonicalRoot],
        timeoutMs: this.timeoutMs,
        stdoutLimitBytes: this.stdoutLimitBytes,
        stderrLimitBytes: this.stderrLimitBytes,
      };
      result = await this.processRunner.runCoalesced(spec);
    } catch {
      return { kind: "failed", summary: this.mutation.launchFailureSummary };
    }

    if (result.cancelled) {
      return { kind: "failed", summary: this.mutation.cancelledSummary };
    }
    if (result.timedOut) {
      return { kind: "failed", summary: this.mutation.timedOutSummary };
    }
    if (result.exitCode === 0) {
      return { kind: "requested" };
    }
    return { kind: "failed", summary: this.mutation.declinedSummary };
  }
}
