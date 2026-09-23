package skillbill.application

import kotlin.coroutines.cancellation.CancellationException

fun Throwable.rethrowIfCooperativeCancellationOrInterruption(): Nothing? {
  when (this) {
    is CancellationException -> throw this
    is InterruptedException -> throw this
  }
  return null
}

inline fun <T> Result<T>.getOrElseUnlessCooperative(onFailure: () -> T): T {
  exceptionOrNull()?.rethrowIfCooperativeCancellationOrInterruption()
  return getOrElse { onFailure() }
}
