package skillbill.engine.experiment.report
import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.EXPERIMENT_REPORT_CONTRACT_VERSION
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import skillbill.contracts.telemetry.TelemetryMeasurementAvailability
import skillbill.experiment.model.ExperimentExecutionMode

object ExperimentReportProjector {
  fun project(pairPayload: Map<String, Any?>, cohort: String): Map<String, Any?> {
    val normalizedCohort = when (cohort) {
      ExperimentExecutionMode.GOAL_PAIR.wireValue -> ExperimentReportPayloadKeys.GOAL_COHORT
      ExperimentReportPayloadKeys.GOAL_COHORT,
      ExperimentReportPayloadKeys.NAVIGATION_COHORT,
      -> cohort
      else -> cohort
    }
    val pairStatus = pairPayload[ExperimentPairPayloadKeys.PAIR_STATUS]?.toString()
    val projection = linkedMapOf<String, Any?>(
      ExperimentReportPayloadKeys.CONTRACT_VERSION to EXPERIMENT_REPORT_CONTRACT_VERSION,
      ExperimentReportPayloadKeys.PAIR_ID to pairPayload[ExperimentPairPayloadKeys.PAIR_ID],
      ExperimentReportPayloadKeys.COHORT to normalizedCohort,
      ExperimentReportPayloadKeys.COMPLETENESS to when (pairStatus) {
        "completed" -> "complete"
        "failed", "cancelled" -> "degraded"
        else -> "incomplete"
      },
      ExperimentReportPayloadKeys.SELECTED_EXPERIMENT_NAMES to
        (pairPayload[ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES] as? List<*>)
          ?.map { it.toString() }
          .orEmpty(),
      ExperimentReportPayloadKeys.DELIVERY_ARM to
        (pairPayload[ExperimentPairPayloadKeys.DELIVERY_ARM]?.toString() ?: "undecided"),
    )
    (pairPayload[ExperimentPairPayloadKeys.FROZEN_INPUT_IDENTITY] as? Map<*, *>)?.let { identity ->
      projection[ExperimentReportPayloadKeys.SOURCE_FINGERPRINTS] = identity.entries
        .filter { entry ->
          entry.key.toString() in setOf(
            ExperimentPairPayloadKeys.REPOSITORY_IDENTITY,
            ExperimentPairPayloadKeys.SOURCE_COMMIT_SHA,
            ExperimentPairPayloadKeys.SOURCE_TREE_SHA,
            ExperimentPairPayloadKeys.SPEC_BUNDLE_HASH,
            ExperimentPairPayloadKeys.EFFECTIVE_CONFIG_HASH,
            ExperimentPairPayloadKeys.SKILL_BILL_VERSION,
          )
        }
        .associate { entry -> entry.key.toString() to entry.value }
    }
    val observationLedger = observationLedger(pairPayload)
    if (observationLedger.isNotEmpty()) {
      projection[ExperimentReportPayloadKeys.RAW_MEASUREMENTS] = observationLedger
      projection[ExperimentReportPayloadKeys.METRIC_COMPARISONS] =
        metricComparisons(observationLedger)
      if (pairPayload[ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND] == null) {
        projection[ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND] =
          spendMap(totalExperimentSpend(observationLedger))
      }
    }
    val armSummaries = pairPayload[ExperimentReportPayloadKeys.ARM_SUMMARIES]
      ?: (pairPayload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
        ?.filterIsInstance<Map<*, *>>()
        ?.mapNotNull { outcome ->
          val armId = outcome[ExperimentPairPayloadKeys.ARM_ID]?.toString() ?: return@mapNotNull null
          val terminalStatus =
            outcome[ExperimentPairPayloadKeys.TERMINAL_STATUS]?.toString() ?: return@mapNotNull null
          mapOf(
            ExperimentReportPayloadKeys.ARM_ID to armId,
            ExperimentReportPayloadKeys.TERMINAL_STATUS to terminalStatus,
          ).plus(
            listOf(
              ExperimentReportPayloadKeys.DELIVERED_PATHS,
              ExperimentReportPayloadKeys.SHORTLISTED_PATHS,
              ExperimentReportPayloadKeys.READ_RECEIPTS,
              ExperimentReportPayloadKeys.ATTEMPT_COUNT,
              ExperimentReportPayloadKeys.LABELLED_CRITERIA,
              ExperimentReportPayloadKeys.TOTAL_CRITERIA,
              ExperimentReportPayloadKeys.PRECISION_AVAILABLE,
              ExperimentReportPayloadKeys.EXCLUDED_PATHS,
              ExperimentReportPayloadKeys.RESTRICTED_BASELINE,
              ExperimentReportPayloadKeys.DELIVERED_CRITERIA,
              ExperimentReportPayloadKeys.RELEVANT_READS,
              ExperimentReportPayloadKeys.PRECISION,
              ExperimentReportPayloadKeys.WORKFLOW_ID,
              ExperimentReportPayloadKeys.WORKTREE_PATH,
              ExperimentReportPayloadKeys.FAILURE_REASON,
            ).mapNotNull { key -> outcome[key]?.let { key to it } }.toMap(),
          )
        }
    armSummaries?.let { projection[ExperimentReportPayloadKeys.ARM_SUMMARIES] = it }
    listOf(
      ExperimentReportPayloadKeys.METRIC_COMPARISONS,
      ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND,
      ExperimentReportPayloadKeys.EXECUTION_COST,
      ExperimentReportPayloadKeys.SETUP_COST,
    ).forEach { key ->
      pairPayload[key]?.let { value ->
        projection[key] = if (key == ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND && value is Number) {
          mapOf(
            ExperimentReportPayloadKeys.AVAILABILITY to TelemetryMeasurementAvailability.MEASURED.wireValue,
            ExperimentReportPayloadKeys.AMOUNT to value.toDouble(),
          )
        } else {
          value
        }
      }
    }
    exclusionReasons(pairPayload).takeIf { it.isNotEmpty() }?.let { reasons ->
      projection[ExperimentReportPayloadKeys.EXCLUSION_REASONS] = reasons
    }
    return projection
  }

  fun renderText(projection: Map<String, Any?>): String = buildString {
    append("Experiment report for pair ${projection[ExperimentReportPayloadKeys.PAIR_ID]} ")
    append("cohort=${projection[ExperimentReportPayloadKeys.COHORT]} ")
    append("completeness=${projection[ExperimentReportPayloadKeys.COMPLETENESS]} ")
    append("delivery_arm=${projection[ExperimentReportPayloadKeys.DELIVERY_ARM]} ")
    append(
      "selected_experiments=" +
        (projection[ExperimentReportPayloadKeys.SELECTED_EXPERIMENT_NAMES] ?: emptyList<Any>()),
    )
    projection[ExperimentReportPayloadKeys.ARM_SUMMARIES]?.let {
      append(" arm_summaries=$it")
    }
    projection[ExperimentReportPayloadKeys.RAW_MEASUREMENTS]?.let {
      append(" raw_measurements=$it")
    }
    projection[ExperimentReportPayloadKeys.METRIC_COMPARISONS]?.let {
      append(" metric_comparisons=$it")
    }
    projection[ExperimentReportPayloadKeys.EXECUTION_COST]?.let {
      append(" execution_cost=$it")
    }
    projection[ExperimentReportPayloadKeys.SETUP_COST]?.let {
      append(" setup_cost=$it")
    }
    projection[ExperimentReportPayloadKeys.EXCLUSION_REASONS]?.let {
      append(" exclusions=$it")
    }
    projection[ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND]?.let {
      append(" total_spend=$it")
    }
  }

  fun renderJson(projection: Map<String, Any?>): String = JsonCodec.mapToJsonString(projection)

  private fun observationLedger(pairPayload: Map<String, Any?>): List<Map<String, Any?>> =
    (pairPayload[ExperimentPairPayloadKeys.OBSERVATION_LEDGER] as? List<*>)
      ?.mapNotNull { value ->
        (value as? Map<*, *>)?.entries
          ?.associate { entry -> entry.key.toString() to entry.value }
      }
      .orEmpty()

  private fun exclusionReasons(pairPayload: Map<String, Any?>): List<String> {
    val declared = (pairPayload[ExperimentReportPayloadKeys.EXCLUSION_REASONS] as? List<*>)
      .orEmpty()
      .map { it.toString() }
    val armFailures = (pairPayload[ExperimentPairPayloadKeys.ARM_OUTCOMES] as? List<*>)
      .orEmpty()
      .filterIsInstance<Map<*, *>>()
      .filter { it[ExperimentPairPayloadKeys.TERMINAL_STATUS] != "completed" }
      .mapNotNull { outcome ->
        outcome[ExperimentPairPayloadKeys.FAILURE_REASON]?.toString()
          ?: outcome[ExperimentPairPayloadKeys.TERMINAL_STATUS]?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { status -> "arm outcome: $status" }
      }
    return (declared + armFailures).distinct().sorted()
  }

  private fun metricComparisons(ledger: List<Map<String, Any?>>): List<Map<String, Any?>> {
    val measurements = ledger.flatMap { observation ->
      val armId = observation[ExperimentObservationPayloadKeys.ARM_ID]?.toString() ?: return@flatMap emptyList()
      (observation[ExperimentObservationPayloadKeys.MEASUREMENTS] as? List<*>)
        .orEmpty()
        .mapNotNull { value ->
          (value as? Map<*, *>)?.entries
            ?.associate { entry -> entry.key.toString() to entry.value }
            ?.let { armId to it }
        }
    }
    return measurements
      .mapNotNull { (_, measurement) -> measurement[ExperimentObservationPayloadKeys.METRIC_ID]?.toString() }
      .toSet()
      .sorted()
      .map { metricId ->
        val control = aggregateMeasurement(measurements, "control", metricId)
        val treatment = aggregateMeasurement(measurements, "treatment", metricId)
        val savings = ExperimentSavingsMath.lowerIsBetterSavings(control, treatment)
        val setupInclusive = if (metricId == ExperimentObservationPayloadKeys.COST_METRIC_ID) {
          ExperimentSavingsMath.lowerIsBetterSetupInclusiveSavings(
            controlExecution = control,
            treatmentExecution = treatment,
            controlSetup = aggregateMeasurement(
              measurements,
              "control",
              ExperimentObservationPayloadKeys.SETUP_COST_METRIC_ID,
            ),
            treatmentSetup = aggregateMeasurement(
              measurements,
              "treatment",
              ExperimentObservationPayloadKeys.SETUP_COST_METRIC_ID,
            ),
          )
        } else {
          null
        }
        linkedMapOf<String, Any?>(
          ExperimentReportPayloadKeys.METRIC_ID to metricId,
          ExperimentReportPayloadKeys.DIRECTION to "lower_is_better",
          ExperimentReportPayloadKeys.CONTROL_VALUE to quantityMap(control),
          ExperimentReportPayloadKeys.TREATMENT_VALUE to quantityMap(treatment),
          ExperimentReportPayloadKeys.ABSOLUTE_SAVINGS to quantityMap(savings.absolute),
          ExperimentReportPayloadKeys.PERCENT_SAVINGS to
            savings.percent.takeIf { it.availability == TelemetryMeasurementAvailability.MEASURED }
              ?.let(::quantityMap),
          ExperimentReportPayloadKeys.PERCENT_SAVINGS_OMITTED_REASON to
            savings.percent.reason.takeIf { savings.percent.availability != TelemetryMeasurementAvailability.MEASURED },
          ExperimentReportPayloadKeys.SETUP_INCLUSIVE_ABSOLUTE_SAVINGS to
            setupInclusive?.absolute?.let(::quantityMap),
          ExperimentReportPayloadKeys.SETUP_INCLUSIVE_PERCENT_SAVINGS to
            setupInclusive?.percent?.takeIf {
              it.availability == TelemetryMeasurementAvailability.MEASURED
            }?.let(::quantityMap),
        ).filterValues { it != null }
      }
  }

  private fun aggregateMeasurement(
    measurements: List<Pair<String, Map<String, Any?>>>,
    armId: String,
    metricId: String,
  ): QuantityWithAvailability {
    val matching = measurements
      .filter { (candidateArm, measurement) ->
        candidateArm == armId && measurement[ExperimentObservationPayloadKeys.METRIC_ID] == metricId
      }
      .map { it.second }
    if (matching.isEmpty()) {
      return QuantityWithAvailability(
        TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
        reason = "no durable measurement",
      )
    }
    val unavailable = matching.firstOrNull { measurement ->
      val availability = TelemetryMeasurementAvailability.fromWireOrUnknown(
        measurement[ExperimentObservationPayloadKeys.AVAILABILITY]?.toString().orEmpty(),
      )
      availability != TelemetryMeasurementAvailability.MEASURED ||
        measurement[ExperimentObservationPayloadKeys.QUANTITY] !is Number
    }
    if (unavailable != null) {
      val availability = TelemetryMeasurementAvailability.fromWireOrUnknown(
        unavailable[ExperimentObservationPayloadKeys.AVAILABILITY]?.toString().orEmpty(),
      )
      return QuantityWithAvailability(
        availability = availability,
        reason = unavailable[ExperimentObservationPayloadKeys.REASON]?.toString()
          ?: "measurement unavailable",
      )
    }
    return QuantityWithAvailability(
      availability = TelemetryMeasurementAvailability.MEASURED,
      quantity = matching.sumOf { it[ExperimentObservationPayloadKeys.QUANTITY].toString().toDouble() },
    )
  }

  private fun quantityMap(value: QuantityWithAvailability): Map<String, Any?> = linkedMapOf<String, Any?>(
    ExperimentReportPayloadKeys.AVAILABILITY to value.availability.wireValue,
    ExperimentReportPayloadKeys.QUANTITY to value.quantity,
    ExperimentReportPayloadKeys.REASON to value.reason,
  ).filterValues { it != null }

  private fun spendMap(value: QuantityWithAvailability): Map<String, Any?> = linkedMapOf<String, Any?>(
    ExperimentReportPayloadKeys.AVAILABILITY to value.availability.wireValue,
    ExperimentReportPayloadKeys.AMOUNT to value.quantity,
    ExperimentReportPayloadKeys.REASON to value.reason,
  ).filterValues { it != null }

  private fun totalExperimentSpend(ledger: List<Map<String, Any?>>): QuantityWithAvailability {
    val measurements = ledger.flatMap { observation ->
      (observation[ExperimentObservationPayloadKeys.MEASUREMENTS] as? List<*>)
        .orEmpty()
        .mapNotNull { it as? Map<*, *> }
        .filter { measurement ->
          measurement[ExperimentObservationPayloadKeys.METRIC_ID] in setOf(
            ExperimentObservationPayloadKeys.COST_METRIC_ID,
            ExperimentObservationPayloadKeys.SETUP_COST_METRIC_ID,
          )
        }
    }
    if (measurements.isEmpty()) {
      return QuantityWithAvailability(
        TelemetryMeasurementAvailability.UNAVAILABLE_INCOMPLETE,
        reason = "no durable cost measurements",
      )
    }
    val unavailable = measurements.firstOrNull { measurement ->
      val availability = TelemetryMeasurementAvailability.fromWireOrUnknown(
        measurement[ExperimentObservationPayloadKeys.AVAILABILITY]?.toString().orEmpty(),
      )
      availability != TelemetryMeasurementAvailability.MEASURED ||
        measurement[ExperimentObservationPayloadKeys.QUANTITY] !is Number
    }
    if (unavailable != null) {
      val availability = TelemetryMeasurementAvailability.fromWireOrUnknown(
        unavailable[ExperimentObservationPayloadKeys.AVAILABILITY]?.toString().orEmpty(),
      )
      return QuantityWithAvailability(
        availability = availability,
        reason = unavailable[ExperimentObservationPayloadKeys.REASON]?.toString()
          ?: "cost measurement unavailable",
      )
    }
    return QuantityWithAvailability(
      availability = TelemetryMeasurementAvailability.MEASURED,
      quantity = measurements.sumOf {
        it[ExperimentObservationPayloadKeys.QUANTITY].toString().toDouble()
      },
    )
  }
}
