import { GoalMutationRepository } from "../application/GoalMutationRepository";
import { PreferenceCachePort } from "../application/PreferenceCachePort";
import { StatusRefreshCoordinator } from "../application/StatusRefreshCoordinator";
import { StatusRepository } from "../application/StatusRepository";
import { StatusClock } from "../domain/StatusClock";
import {
  CliGoalMutationRepository,
  GOAL_PAUSE_MUTATION,
  GOAL_STOP_MUTATION,
} from "../infrastructure/cli/CliGoalMutationRepository";
import { ProcessRunner } from "../infrastructure/cli/ProcessRunner";
import { SkillBillStatusViewModel } from "../presentation/SkillBillStatusViewModel";

export interface GoalMutationRepositories {
  readonly pause: GoalMutationRepository;
  readonly stop: GoalMutationRepository;
}

export function createGoalMutationRepositories(
  preferences: PreferenceCachePort,
  pauseProcessRunner: ProcessRunner,
  stopProcessRunner: ProcessRunner,
): GoalMutationRepositories {
  return {
    pause: new CliGoalMutationRepository(GOAL_PAUSE_MUTATION, preferences, pauseProcessRunner),
    stop: new CliGoalMutationRepository(GOAL_STOP_MUTATION, preferences, stopProcessRunner),
  };
}

export class StatusCompositionRoot {
  constructor(
    readonly preferences: PreferenceCachePort,
    readonly processRunner: ProcessRunner,
    readonly pauseProcessRunner: ProcessRunner,
    readonly stopProcessRunner: ProcessRunner,
    readonly statusRepository: StatusRepository,
    readonly coordinator: StatusRefreshCoordinator,
    readonly viewModel: SkillBillStatusViewModel,
    readonly goalPauseRepository: GoalMutationRepository,
    readonly goalStopRepository: GoalMutationRepository,
  ) {}

  dispose(): void {
    this.viewModel.dispose();
    this.coordinator.dispose();
    this.processRunner.cancelAll();
    this.pauseProcessRunner.cancelAll();
    this.stopProcessRunner.cancelAll();
  }

  static createForTest(options: {
    preferences: PreferenceCachePort;
    statusRepository: StatusRepository;
    projectRoot: string;
    clock?: StatusClock;
    onCancelProcesses?: () => void;
    processRunner?: ProcessRunner;
    pauseProcessRunner?: ProcessRunner;
    stopProcessRunner?: ProcessRunner;
  }): StatusCompositionRoot {
    const clock = options.clock ?? StatusClock.system();
    const processRunner = options.processRunner ?? new ProcessRunner();
    const pauseProcessRunner = options.pauseProcessRunner ?? new ProcessRunner();
    const stopProcessRunner = options.stopProcessRunner ?? new ProcessRunner();
    const coordinator = new StatusRefreshCoordinator(
      options.statusRepository,
      options.preferences,
      options.projectRoot,
      options.onCancelProcesses ?? (() => processRunner.cancelAll()),
    );
    const viewModel = new SkillBillStatusViewModel(coordinator, clock);
    const mutations = createGoalMutationRepositories(
      options.preferences,
      pauseProcessRunner,
      stopProcessRunner,
    );
    return new StatusCompositionRoot(
      options.preferences,
      processRunner,
      pauseProcessRunner,
      stopProcessRunner,
      options.statusRepository,
      coordinator,
      viewModel,
      mutations.pause,
      mutations.stop,
    );
  }
}
