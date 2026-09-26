package skillbill.error.featuretask

import skillbill.error.core.ShellContentContractException

class UnknownPhaseStepError(
  val stepId: String,
) : ShellContentContractException("Phase step '$stepId' does not belong to any phase slot.")

class DuplicatePhaseStrategyError(
  val slot: String,
  val strategyId: String,
) : ShellContentContractException("Phase slot '$slot' registers strategy '$strategyId' more than once.")

class PhaseStrategyStepOutsideSlotError(
  val slot: String,
  val strategyId: String,
  val stepId: String,
) : ShellContentContractException(
    "Phase strategy '$strategyId' for slot '$slot' declares step '$stepId' outside that slot.",
  )

class UnknownPhaseStrategyError(
  val slot: String,
  val strategyId: String,
) : ShellContentContractException("Phase slot '$slot' has no strategy '$strategyId'.")

class UnregisteredPhaseStrategySelectionError(
  val slot: String,
  val strategyId: String,
) : ShellContentContractException(
    "Phase strategy selection for slot '$slot' names unregistered strategy '$strategyId'.",
  )
