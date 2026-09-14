export type GoalMutationOutcome = { kind: "requested" } | { kind: "failed"; summary: string };

export interface GoalMutationRepository {
  requestMutation(projectRoot: string, issueKey: string): Promise<GoalMutationOutcome>;
}
