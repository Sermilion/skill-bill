package skillbill.workflow.taskruntime.model.audit

import skillbill.workflow.taskruntime.model.core.MAX_ACCEPTANCE_CRITERION_ORDINAL

fun canonicalAcceptanceCriterionRef(ordinal: Int): String {
  require(ordinal in 1..MAX_ACCEPTANCE_CRITERION_ORDINAL) {
    "Acceptance criterion ordinal must be 1-based and at most $MAX_ACCEPTANCE_CRITERION_ORDINAL, was $ordinal."
  }
  return "AC-" + ordinal.toString().padStart(ACCEPTANCE_CRITERION_REF_DIGITS, '0')
}

private const val ACCEPTANCE_CRITERION_REF_DIGITS: Int = 3
