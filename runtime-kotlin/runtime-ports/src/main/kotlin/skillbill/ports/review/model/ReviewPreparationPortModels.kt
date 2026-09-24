package skillbill.ports.review.model

import skillbill.ports.review.preparation.ReviewBuildTestFactsPort
import skillbill.ports.review.preparation.ReviewGuidancePort
import skillbill.ports.review.preparation.ReviewLaneSelectionPort
import skillbill.ports.review.preparation.ReviewLearningsPort
import skillbill.ports.review.preparation.ReviewScopeResolverPort
import skillbill.ports.review.preparation.ReviewStackRoutingPort
import skillbill.review.context.model.commit.ReviewCommitCoverageFact
import skillbill.review.context.model.commit.ReviewCommitLaneRoutingMatrix
import skillbill.review.context.model.commit.ReviewCommitUnit
import skillbill.review.context.model.execution.ReviewLaneDecision
import skillbill.review.context.model.hunk.ReviewChangedHunk

data class ReviewScopeFacts(
  val repositoryIdentity: String,
  val baseRevision: String,
  val headRevision: String,
  val status: String,
  val changedHunks: List<ReviewChangedHunk>,
  val commitUnits: List<ReviewCommitUnit>,
  val coverageFact: ReviewCommitCoverageFact,
)

data class ReviewLaneSelection(
  val decisions: List<ReviewLaneDecision>,
  val routingMatrix: ReviewCommitLaneRoutingMatrix,
)

data class ReviewStackRoutingFacts(
  val stack: String?,
  val pack: String?,
  val addOns: List<String>,
  val composedLayers: List<String>,
)

data class ReviewFactPorts(
  val scope: ReviewScopeResolverPort,
  val stackRouting: ReviewStackRoutingPort,
  val guidance: ReviewGuidancePort,
  val learnings: ReviewLearningsPort,
  val buildTestFacts: ReviewBuildTestFactsPort,
  val laneSelection: ReviewLaneSelectionPort,
)
