package skillbill.engine.featuretask.slot

/**
 * Executes one phase step for any strategy: composes the step prompt, launches the agent, reads the step's
 * settlement, and returns the uniform step output. Every strategy owns its own instance.
 */
interface PhaseRunner {
  /** Runs [input] against the per-call [state] and returns the step's uniform output. */
  fun run(
    input: PhaseStepInput,
    state: PhaseRunState,
  ): PhaseStepOutput
}
