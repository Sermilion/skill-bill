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

class InvalidSkeletonDefinitionError(
  val definitionId: String,
  val slots: List<String>,
) : ShellContentContractException(
    "Skeleton definition '$definitionId' must list distinct phase slots in canonical order, was $slots.",
  )

class PhaseStrategySelectionSlotMismatchError(
  val definitionId: String,
  val slot: String,
) : ShellContentContractException(
    "Phase strategy selection for skeleton definition '$definitionId' must bind exactly its slots; " +
      "slot '$slot' is unbound or outside the definition.",
  )

class UnknownQualityGateSelectionError(
  val value: String,
  val allowedValues: List<String>,
) : ShellContentContractException(
    "Unknown quality-gate selection '$value'; expected one of ${allowedValues.joinToString(", ")}.",
  )

class UnregisteredPhaseStrategySelectionError(
  val slot: String,
  val strategyId: String,
) : ShellContentContractException(
    "Phase strategy selection for slot '$slot' names unregistered strategy '$strategyId'.",
  )
