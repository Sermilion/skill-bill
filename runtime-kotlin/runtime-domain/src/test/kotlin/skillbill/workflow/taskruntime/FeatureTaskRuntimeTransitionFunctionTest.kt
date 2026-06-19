package skillbill.workflow.taskruntime

import skillbill.error.InvalidWorkflowStateSchemaError
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeBackwardEdge
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeNextPhase
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewFinding
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewSeverity
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeReviewVerdict
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeTransitionDeclaration
import skillbill.workflow.taskruntime.model.FeatureTaskRuntimeVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FeatureTaskRuntimeTransitionFunctionTest {
  private val pipeline = listOf("a", "b", "c", "d")
  private val needsFix = FeatureTaskRuntimeVerdict("needs_fix")
  private val edge = FeatureTaskRuntimeBackwardEdge(
    fromPhaseId = "c",
    triggeringVerdict = needsFix,
    destinationPhaseId = "b",
    loopId = "c-to-b",
    perEdgeCap = 2,
  )
  private val cyclic = FeatureTaskRuntimeTransitionDeclaration(
    forwardPhaseIds = pipeline,
    backwardEdges = listOf(edge),
  )
  private val forwardOnly = FeatureTaskRuntimeTransitionDeclaration(forwardPhaseIds = pipeline)

  @Test
  fun `default advance with no matching edge progresses to the next forward index`() {
    listOf("a" to "b", "b" to "c", "c" to "d").forEach { (current, expectedNext) ->
      val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = cyclic,
        currentPhaseId = current,
        verdict = FeatureTaskRuntimeVerdict.ADVANCE,
        edgeIterationCount = 0,
      )
      val next = assertIs<FeatureTaskRuntimeNextPhase.Next>(transition)
      assertEquals(expectedNext, next.phaseId)
      assertEquals(null, next.loopId)
      assertEquals(null, next.edgeIteration)
    }
  }

  @Test
  fun `pipeline end terminates with advance`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = cyclic,
      currentPhaseId = "d",
      verdict = FeatureTaskRuntimeVerdict.ADVANCE,
      edgeIterationCount = 0,
    )
    assertIs<FeatureTaskRuntimeNextPhase.TerminalAdvance>(transition)
  }

  @Test
  fun `matching backward edge below cap re-enters the destination tagged with loop and iteration`() {
    (0 until edge.perEdgeCap).forEach { alreadyFired ->
      val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = cyclic,
        currentPhaseId = "c",
        verdict = needsFix,
        edgeIterationCount = alreadyFired,
      )
      val next = assertIs<FeatureTaskRuntimeNextPhase.Next>(transition)
      assertEquals("b", next.phaseId)
      assertEquals("c-to-b", next.loopId)
      assertEquals(alreadyFired + 1, next.edgeIteration)
    }
  }

  @Test
  fun `matching backward edge at cap blocks loudly with loop id iteration and unresolved verdict`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = cyclic,
      currentPhaseId = "c",
      verdict = needsFix,
      edgeIterationCount = edge.perEdgeCap,
    )
    val block = assertIs<FeatureTaskRuntimeNextPhase.TerminalBlock>(transition)
    assertEquals("c-to-b", block.loopId)
    assertEquals(edge.perEdgeCap, block.edgeIteration)
    assertEquals(needsFix, block.unresolvedVerdict)
  }

  @Test
  fun `non-triggering verdict at the edge phase falls through to the forward edge`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = cyclic,
      currentPhaseId = "c",
      verdict = FeatureTaskRuntimeVerdict.ADVANCE,
      edgeIterationCount = 5,
    )
    assertEquals("d", assertIs<FeatureTaskRuntimeNextPhase.Next>(transition).phaseId)
  }

  @Test
  fun `edge-free declaration is forward-only for every phase verdict and iteration`() {
    val verdicts = listOf(FeatureTaskRuntimeVerdict.ADVANCE, needsFix, FeatureTaskRuntimeVerdict("other"))
    pipeline.forEachIndexed { index, phaseId ->
      verdicts.forEach { verdict ->
        (0..3).forEach { iteration ->
          val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
            declaration = forwardOnly,
            currentPhaseId = phaseId,
            verdict = verdict,
            edgeIterationCount = iteration,
          )
          if (index < pipeline.lastIndex) {
            assertEquals(pipeline[index + 1], assertIs<FeatureTaskRuntimeNextPhase.Next>(transition).phaseId)
          } else {
            assertIs<FeatureTaskRuntimeNextPhase.TerminalAdvance>(transition)
          }
        }
      }
    }
  }

  @Test
  fun `verdict round trips through its wire value`() {
    assertEquals("advance", FeatureTaskRuntimeVerdict.ADVANCE.wireValue)
    assertEquals(needsFix, FeatureTaskRuntimeVerdict.fromWire(needsFix.wireValue))
    assertEquals(FeatureTaskRuntimeVerdict.ADVANCE, FeatureTaskRuntimeVerdict.fromWire("advance"))
  }

  @Test
  fun `verdict fromWire loud-fails on a blank wire value`() {
    assertFailsWith<InvalidWorkflowStateSchemaError> { FeatureTaskRuntimeVerdict.fromWire("  ") }
  }

  // --- Loop-only forward skip (Subtask 4) -------------------------------------------------------

  // A pipeline mirroring the real review_fix topology: `fix` is loop-only, sitting between `impl` and
  // `review` so the forward edge skips it and a clean run advances `impl` -> `review`.
  private val loopPipeline = listOf("plan", "impl", "fix", "review", "audit")
  private val reviewFixEdge = FeatureTaskRuntimeBackwardEdge(
    fromPhaseId = "review",
    triggeringVerdict = FeatureTaskRuntimeVerdict.CHANGES_REQUESTED,
    destinationPhaseId = "fix",
    loopId = "review_fix",
    perEdgeCap = 3,
  )
  private val loopDeclaration = FeatureTaskRuntimeTransitionDeclaration(
    forwardPhaseIds = loopPipeline,
    backwardEdges = listOf(reviewFixEdge),
    loopOnlyPhaseIds = setOf("fix"),
  )

  @Test
  fun `forward edge skips a loop-only phase to the next non-loop-only phase`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = loopDeclaration,
      currentPhaseId = "impl",
      verdict = FeatureTaskRuntimeVerdict.ADVANCE,
      edgeIterationCount = 0,
    )
    assertEquals("review", assertIs<FeatureTaskRuntimeNextPhase.Next>(transition).phaseId)
  }

  @Test
  fun `loop-only phase forward-advances to the next non-loop-only phase`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = loopDeclaration,
      currentPhaseId = "fix",
      verdict = FeatureTaskRuntimeVerdict.ADVANCE,
      edgeIterationCount = 0,
    )
    assertEquals("review", assertIs<FeatureTaskRuntimeNextPhase.Next>(transition).phaseId)
  }

  @Test
  fun `loop-only phase is reachable only via a backward edge destination`() {
    // No forward transition from any non-loop-only phase ever yields the loop-only `fix` phase.
    loopPipeline.filterNot { it == "fix" }.forEach { phaseId ->
      val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = loopDeclaration,
        currentPhaseId = phaseId,
        verdict = FeatureTaskRuntimeVerdict.ADVANCE,
        edgeIterationCount = 0,
      )
      val landed = (transition as? FeatureTaskRuntimeNextPhase.Next)?.phaseId
      assertTrue(landed != "fix", "forward advance from '$phaseId' must not reach the loop-only phase")
    }
    // The backward edge is the only path that reaches it.
    val backward = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = loopDeclaration,
      currentPhaseId = "review",
      verdict = FeatureTaskRuntimeVerdict.CHANGES_REQUESTED,
      edgeIterationCount = 0,
    )
    assertEquals("fix", assertIs<FeatureTaskRuntimeNextPhase.Next>(backward).phaseId)
  }

  @Test
  fun `empty loopOnlyPhaseIds leaves the forward advance unchanged`() {
    val noLoopOnly = loopDeclaration.copy(loopOnlyPhaseIds = emptySet())
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = noLoopOnly,
      currentPhaseId = "impl",
      verdict = FeatureTaskRuntimeVerdict.ADVANCE,
      edgeIterationCount = 0,
    )
    assertEquals("fix", assertIs<FeatureTaskRuntimeNextPhase.Next>(transition).phaseId)
  }

  @Test
  fun `loopOnlyPhaseIds must be a subset of the forward pipeline`() {
    assertFailsWith<IllegalArgumentException> {
      FeatureTaskRuntimeTransitionDeclaration(
        forwardPhaseIds = loopPipeline,
        loopOnlyPhaseIds = setOf("not_in_pipeline"),
      )
    }
  }

  // --- review_fix transition matrix (verdict x iteration) ---------------------------------------

  @Test
  fun `review approved forwards past the loop-only fix phase to audit`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = loopDeclaration,
      currentPhaseId = "review",
      verdict = FeatureTaskRuntimeVerdict.APPROVED,
      edgeIterationCount = 0,
    )
    assertEquals("audit", assertIs<FeatureTaskRuntimeNextPhase.Next>(transition).phaseId)
  }

  @Test
  fun `review changes_requested under cap re-enters fix tagged with review_fix and incrementing iteration`() {
    (0 until reviewFixEdge.perEdgeCap).forEach { alreadyFired ->
      val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
        declaration = loopDeclaration,
        currentPhaseId = "review",
        verdict = FeatureTaskRuntimeVerdict.CHANGES_REQUESTED,
        edgeIterationCount = alreadyFired,
      )
      val next = assertIs<FeatureTaskRuntimeNextPhase.Next>(transition)
      assertEquals("fix", next.phaseId)
      assertEquals("review_fix", next.loopId)
      assertEquals(alreadyFired + 1, next.edgeIteration)
    }
  }

  @Test
  fun `review changes_requested at cap blocks with review_fix and the unresolved verdict`() {
    val transition = FeatureTaskRuntimeTransitionFunction.nextTransition(
      declaration = loopDeclaration,
      currentPhaseId = "review",
      verdict = FeatureTaskRuntimeVerdict.CHANGES_REQUESTED,
      edgeIterationCount = reviewFixEdge.perEdgeCap,
    )
    val block = assertIs<FeatureTaskRuntimeNextPhase.TerminalBlock>(transition)
    assertEquals("review_fix", block.loopId)
    assertEquals(reviewFixEdge.perEdgeCap, block.edgeIteration)
    assertEquals(FeatureTaskRuntimeVerdict.CHANGES_REQUESTED, block.unresolvedVerdict)
  }

  // --- verdict constants + findings -> verdict helper -------------------------------------------

  @Test
  fun `review verdict constants carry their stable wire values`() {
    assertEquals("approved", FeatureTaskRuntimeVerdict.APPROVED.wireValue)
    assertEquals("changes_requested", FeatureTaskRuntimeVerdict.CHANGES_REQUESTED.wireValue)
    assertEquals(FeatureTaskRuntimeVerdict.APPROVED, FeatureTaskRuntimeVerdict.fromWire("approved"))
    assertEquals(
      FeatureTaskRuntimeVerdict.CHANGES_REQUESTED,
      FeatureTaskRuntimeVerdict.fromWire("changes_requested"),
    )
  }

  @Test
  fun `findings with a Blocker or Major derive changes_requested and expose the unresolved findings`() {
    listOf(FeatureTaskRuntimeReviewSeverity.BLOCKER, FeatureTaskRuntimeReviewSeverity.MAJOR).forEach { severity ->
      val verdict = FeatureTaskRuntimeReviewVerdict(
        listOf(
          FeatureTaskRuntimeReviewFinding(FeatureTaskRuntimeReviewSeverity.NIT, "tidy import"),
          FeatureTaskRuntimeReviewFinding(severity, "blocking issue"),
        ),
      )
      assertEquals(FeatureTaskRuntimeVerdict.CHANGES_REQUESTED, verdict.verdict)
      assertEquals(listOf("blocking issue"), verdict.unresolvedFindings.map { it.message })
    }
  }

  @Test
  fun `findings with only Minor or Nit or no findings derive approved`() {
    assertEquals(FeatureTaskRuntimeVerdict.APPROVED, FeatureTaskRuntimeReviewVerdict(emptyList()).verdict)
    val minorOnly = FeatureTaskRuntimeReviewVerdict(
      listOf(
        FeatureTaskRuntimeReviewFinding(FeatureTaskRuntimeReviewSeverity.MINOR, "consider renaming"),
        FeatureTaskRuntimeReviewFinding(FeatureTaskRuntimeReviewSeverity.NIT, "trailing space"),
      ),
    )
    assertEquals(FeatureTaskRuntimeVerdict.APPROVED, minorOnly.verdict)
    assertTrue(minorOnly.unresolvedFindings.isEmpty())
  }

  @Test
  fun `review severity fromWire loud-fails on an unknown value`() {
    assertFailsWith<IllegalArgumentException> { FeatureTaskRuntimeReviewSeverity.fromWire("catastrophic") }
  }
}
