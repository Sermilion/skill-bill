package skillbill.engine.experiment.report

import skillbill.contracts.JsonCodec
import skillbill.contracts.experiment.ExperimentObservationPayloadKeys
import skillbill.contracts.experiment.ExperimentPairPayloadKeys
import skillbill.contracts.experiment.ExperimentReportPayloadKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperimentReportProjectorTest {
  @Test
  fun `goal pair payload projects to the report cohort and retains durable report fields`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-1",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "completed",
        ExperimentReportPayloadKeys.EXCLUSION_REASONS to listOf("treatment setup unavailable"),
      ),
      cohort = "goal_pair",
    )

    assertEquals("goal", projection[ExperimentReportPayloadKeys.COHORT])
    assertEquals("complete", projection[ExperimentReportPayloadKeys.COMPLETENESS])
    assertEquals(
      listOf("treatment setup unavailable"),
      projection[ExperimentReportPayloadKeys.EXCLUSION_REASONS],
    )
    assertTrue(ExperimentReportProjector.renderText(projection).contains("pair-1"))
  }

  @Test
  fun `durable arm outcomes become report summaries without replaying the pair`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-2",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "running",
        ExperimentPairPayloadKeys.ARM_OUTCOMES to listOf(
          mapOf(
            ExperimentPairPayloadKeys.ARM_ID to "control",
            ExperimentPairPayloadKeys.TERMINAL_STATUS to "completed",
          ),
        ),
      ),
      cohort = "goal_pair",
    )

    assertEquals("incomplete", projection[ExperimentReportPayloadKeys.COMPLETENESS])
    assertEquals(
      listOf(
        mapOf(
          ExperimentReportPayloadKeys.ARM_ID to "control",
          ExperimentReportPayloadKeys.TERMINAL_STATUS to "completed",
        ),
      ),
      projection[ExperimentReportPayloadKeys.ARM_SUMMARIES],
    )
  }

  @Test
  fun `failed arm outcomes become durable exclusion reasons`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-failed-arm",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "failed",
        ExperimentPairPayloadKeys.ARM_OUTCOMES to listOf(
          mapOf(
            ExperimentPairPayloadKeys.ARM_ID to "treatment",
            ExperimentPairPayloadKeys.TERMINAL_STATUS to "failed",
            ExperimentPairPayloadKeys.FAILURE_REASON to "provider stopped before usage settled",
          ),
        ),
      ),
      cohort = "goal_pair",
    )

    assertEquals(
      listOf("provider stopped before usage settled"),
      projection[ExperimentReportPayloadKeys.EXCLUSION_REASONS],
    )
  }

  @Test
  fun `text report retains execution and setup costs from the durable projection`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-costs",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "completed",
        ExperimentReportPayloadKeys.EXECUTION_COST to 180.0,
        ExperimentReportPayloadKeys.SETUP_COST to 30.0,
        ExperimentReportPayloadKeys.TOTAL_EXPERIMENT_SPEND to 210.0,
      ),
      cohort = "goal_pair",
    )

    assertEquals(180.0, projection[ExperimentReportPayloadKeys.EXECUTION_COST])
    assertEquals(30.0, projection[ExperimentReportPayloadKeys.SETUP_COST])
    assertTrue(ExperimentReportProjector.renderText(projection).contains("amount=210.0"))
    assertTrue(ExperimentReportProjector.renderText(projection).contains("setup_cost=30.0"))
  }

  @Test
  fun `durable observations aggregate per arm and preserve unavailable reasons`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-3",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "completed",
        ExperimentPairPayloadKeys.OBSERVATION_LEDGER to listOf(
          mapOf(
            ExperimentObservationPayloadKeys.ARM_ID to "control",
            ExperimentObservationPayloadKeys.MEASUREMENTS to listOf(
              mapOf(
                ExperimentObservationPayloadKeys.METRIC_ID to "cost",
                ExperimentObservationPayloadKeys.AVAILABILITY to "measured",
                ExperimentObservationPayloadKeys.QUANTITY to 100.0,
              ),
            ),
          ),
          mapOf(
            ExperimentObservationPayloadKeys.ARM_ID to "treatment",
            ExperimentObservationPayloadKeys.MEASUREMENTS to listOf(
              mapOf(
                ExperimentObservationPayloadKeys.METRIC_ID to "cost",
                ExperimentObservationPayloadKeys.AVAILABILITY to "unavailable_incomplete",
                ExperimentObservationPayloadKeys.REASON to "usage was not reported",
              ),
            ),
          ),
        ),
      ),
      cohort = "goal_pair",
    )

    val comparison = (projection[ExperimentReportPayloadKeys.METRIC_COMPARISONS] as List<*>).single() as Map<*, *>
    val treatment = comparison[ExperimentReportPayloadKeys.TREATMENT_VALUE] as Map<*, *>
    assertEquals("unavailable_incomplete", treatment[ExperimentReportPayloadKeys.AVAILABILITY])
    assertEquals("usage was not reported", treatment[ExperimentReportPayloadKeys.REASON])
    assertEquals("usage was not reported", comparison[ExperimentReportPayloadKeys.PERCENT_SAVINGS_OMITTED_REASON])
    val text = ExperimentReportProjector.renderText(projection)
    assertTrue(text.contains("metric_comparisons="))
    assertTrue(text.contains("usage was not reported"))
  }

  @Test
  fun `json and text render the same durable projection without provider replay`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-parity",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "completed",
        ExperimentPairPayloadKeys.OBSERVATION_LEDGER to listOf(
          mapOf(
            ExperimentObservationPayloadKeys.ARM_ID to "control",
            ExperimentObservationPayloadKeys.MEASUREMENTS to listOf(
              mapOf(
                ExperimentObservationPayloadKeys.METRIC_ID to "cost",
                ExperimentObservationPayloadKeys.AVAILABILITY to "measured",
                ExperimentObservationPayloadKeys.QUANTITY to 100.0,
              ),
            ),
          ),
        ),
      ),
      cohort = "goal_pair",
    )

    val jsonProjection = requireNotNull(
      JsonCodec.anyToStringAnyMap(
        JsonCodec.jsonElementToValue(
          requireNotNull(JsonCodec.parseObjectOrNull(ExperimentReportProjector.renderJson(projection))),
        ),
      ),
    )

    assertEquals(
      ExperimentReportProjector.renderJson(projection),
      JsonCodec.mapToJsonString(jsonProjection),
    )
    assertTrue(ExperimentReportProjector.renderText(projection).contains("cost"))
  }

  @Test
  fun `durable cost observations expose execution and setup inclusive savings`() {
    val projection = ExperimentReportProjector.project(
      pairPayload = mapOf(
        ExperimentPairPayloadKeys.PAIR_ID to "pair-savings",
        ExperimentPairPayloadKeys.SELECTED_EXPERIMENT_NAMES to listOf("fixture-goal"),
        ExperimentPairPayloadKeys.DELIVERY_ARM to "control",
        ExperimentPairPayloadKeys.PAIR_STATUS to "completed",
        ExperimentPairPayloadKeys.OBSERVATION_LEDGER to listOf(
          observation("control", 100.0, 0.0),
          observation("treatment", 80.0, 30.0),
        ),
      ),
      cohort = "goal_pair",
    )

    val comparison = (projection[ExperimentReportPayloadKeys.METRIC_COMPARISONS] as List<*>)
      .single { (it as Map<*, *>)[ExperimentReportPayloadKeys.METRIC_ID] == "cost" } as Map<*, *>
    assertEquals(
      20.0,
      (
        (comparison[ExperimentReportPayloadKeys.ABSOLUTE_SAVINGS] as Map<*, *>)[
          ExperimentReportPayloadKeys.QUANTITY,
        ]
        ),
    )
    assertEquals(
      -10.0,
      (
        (comparison[ExperimentReportPayloadKeys.SETUP_INCLUSIVE_ABSOLUTE_SAVINGS] as Map<*, *>)[
          ExperimentReportPayloadKeys.QUANTITY,
        ]
        ),
    )
  }

  private fun observation(armId: String, cost: Double, setupCost: Double): Map<String, Any?> = mapOf(
    ExperimentObservationPayloadKeys.ARM_ID to armId,
    ExperimentObservationPayloadKeys.MEASUREMENTS to listOf(
      mapOf(
        ExperimentObservationPayloadKeys.METRIC_ID to ExperimentObservationPayloadKeys.COST_METRIC_ID,
        ExperimentObservationPayloadKeys.AVAILABILITY to "measured",
        ExperimentObservationPayloadKeys.QUANTITY to cost,
      ),
      mapOf(
        ExperimentObservationPayloadKeys.METRIC_ID to ExperimentObservationPayloadKeys.SETUP_COST_METRIC_ID,
        ExperimentObservationPayloadKeys.AVAILABILITY to "measured",
        ExperimentObservationPayloadKeys.QUANTITY to setupCost,
      ),
    ),
  )
}
