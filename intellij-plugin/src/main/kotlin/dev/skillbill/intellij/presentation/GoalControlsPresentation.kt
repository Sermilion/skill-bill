package dev.skillbill.intellij.presentation

import dev.skillbill.intellij.domain.FEATURE_GOAL_WORKFLOW_FAMILY


object GoalControlsPresentation {
    fun controlsFor(state: SkillBillStatusUiState): List<GoalControlDescriptor> {

        if (state !is SkillBillStatusUiState.Active) return emptyList()
        if (state.workflowFamily != FEATURE_GOAL_WORKFLOW_FAMILY) return emptyList()
        val issueKey = state.issueKey?.takeIf { it.isNotBlank() } ?: return emptyList()



        val pauseAlreadyRequested = state.pauseRequested == true
        return listOf(
            GoalControlDescriptor(
                kind = GoalControlKind.STOP,
                issueKey = issueKey,
                text = "Stop goal",
                enabled = true,
                accessibleName = "Stop Skill Bill goal $issueKey now",
            ),
            GoalControlDescriptor(
                kind = GoalControlKind.PAUSE,
                issueKey = issueKey,


                text = if (pauseAlreadyRequested) "Pause requested" else "Pause after current subtask",
                enabled = !pauseAlreadyRequested,
                accessibleName = if (pauseAlreadyRequested) {
                    "Pause already requested for Skill Bill goal $issueKey; it takes effect after the current subtask"
                } else {
                    "Pause Skill Bill goal $issueKey after the current subtask"
                },
            ),
        )
    }
}

enum class GoalControlKind {
    STOP,
    PAUSE,
}


data class GoalControlDescriptor(
    val kind: GoalControlKind,
    val issueKey: String,
    val text: String,
    val enabled: Boolean,
    val accessibleName: String,
)
