package skillbill.ports.repository.model

sealed interface OriginScopeKey {
  data class Resolved(val key: String) : OriginScopeKey

  data class Unavailable(val reason: String) : OriginScopeKey
}
