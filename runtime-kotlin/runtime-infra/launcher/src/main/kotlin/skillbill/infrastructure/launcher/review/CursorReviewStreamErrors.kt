package skillbill.infrastructure.launcher.review

open class CursorReviewStreamError(
  message: String,
  cause: Throwable? = null,
) : Exception(message, cause)

class CursorReviewStreamMalformedError(
  message: String,
  cause: Throwable? = null,
) : CursorReviewStreamError(message, cause)

internal class CursorReviewStreamEmptyError(message: String) : CursorReviewStreamError(message)

internal class CursorReviewStreamForbiddenOperationError(message: String) : CursorReviewStreamError(message)

internal class CursorReviewStreamProviderFailureError(
  message: String,
  cause: Throwable? = null,
) : CursorReviewStreamError(message, cause)

internal class CursorReviewStreamTerminationError(message: String) : CursorReviewStreamError(message)
