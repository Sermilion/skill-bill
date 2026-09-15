package skillbill.review

import skillbill.review.context.model.ReviewLaneReviewDisposition
import skillbill.review.model.ReviewLaneResolutionState
import skillbill.review.model.ReviewRunLane
import skillbill.review.plan.model.ReviewLaunchPlan

object ReviewRunLaneResolver {
  fun lanesToResume(lanes: List<ReviewRunLane>): List<ReviewRunLane> =
    lanes.filter { it.reviewDisposition != ReviewLaneReviewDisposition.COMPLETE }

  fun resolve(plan: ReviewLaunchPlan, reportedLaneNames: List<String>): List<ReviewRunLane> {
    val reported = reportedLaneNames.map(String::trim).filter(String::isNotEmpty).toSet()
    val planned = plan.lanes.map { lane ->
      ReviewRunLane(
        laneSkillName = lane.skillName,
        packSlug = lane.packSlug,
        area = lane.area,
        depth = lane.depth,
        required = lane.required,
        orderIndex = lane.orderIndex,
        originLayerChain = lane.originLayerChain,
        resolutionState = if (lane.skillName in reported || lane.area in reported) {
          ReviewLaneResolutionState.RESOLVED
        } else {
          ReviewLaneResolutionState.UNRESOLVED
        },

        reviewDisposition = ReviewLaneReviewDisposition.INCOMPLETE,
      )
    }
    val plannedNames = planned.flatMap { listOf(it.laneSkillName, it.area) }.toSet()
    val unmatched = reported.filter { it !in plannedNames }
    return planned + unmatched.mapIndexed { index, reportedName ->
      ReviewRunLane(
        laneSkillName = reportedName,
        packSlug = UNRESOLVED_ATTRIBUTION,
        area = UNRESOLVED_ATTRIBUTION,
        depth = 0,
        required = false,
        orderIndex = planned.size + index,
        originLayerChain = emptyList(),
        resolutionState = ReviewLaneResolutionState.UNRESOLVED,
        reviewDisposition = ReviewLaneReviewDisposition.INCOMPLETE,
      )
    }
  }
}
