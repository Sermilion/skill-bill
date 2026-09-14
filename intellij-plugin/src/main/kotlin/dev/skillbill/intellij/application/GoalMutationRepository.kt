package dev.skillbill.intellij.application

import java.nio.file.Path

fun interface GoalMutationRepository {
    suspend fun requestMutation(projectRoot: Path, issueKey: String): GoalMutationOutcome
}

sealed class GoalMutationOutcome {
    data object Requested : GoalMutationOutcome()

    data class Failed(val summary: String) : GoalMutationOutcome()
}
