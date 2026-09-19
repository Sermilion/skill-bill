package skillbill.ports.review.model
import skillbill.error.shellcontent.InvalidReviewContextSchemaError
import skillbill.review.context.model.execution.requireRepositoryRelativePath
sealed interface ReviewEvidenceCoordinates {
  data class Committed(val revision: String) : ReviewEvidenceCoordinates {
    init {
      if (revision.isBlank()) throw InvalidReviewContextSchemaError("review-source", "Committed revision is required.")
    }
  }

  data class Checkpoint(
    val kind: Kind,
    val files: Map<
      String,
      ReviewCheckpointFileIdentity,
      >,
  ) : ReviewEvidenceCoordinates {
    enum class Kind { INDEX, WORKTREE }

    init {
      files.keys.forEach(::requireRepositoryRelativePath)
    }
  }
}
