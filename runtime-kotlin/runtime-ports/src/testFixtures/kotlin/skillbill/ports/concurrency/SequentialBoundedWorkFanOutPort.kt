package skillbill.ports.concurrency

import kotlin.coroutines.cancellation.CancellationException

object SequentialBoundedWorkFanOutPort : BoundedWorkFanOutPort {
  override fun <T> runBounded(
    maxInFlight: Int,
    units: List<() -> T>,
  ): List<Result<T>> =
    units.map { unit ->
      val result = runCatching { unit() }
      (result.exceptionOrNull() as? CancellationException)?.let { throw it }
      result
    }

  override fun <T> runExclusively(action: () -> T): T = action()
}
