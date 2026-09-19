package skillbill.error.core
import skillbill.error.featuretask.reason
import skillbill.error.shellcontent.operation
import skillbill.error.shellcontent.reason

sealed class RejectedOutputDiagnosticError(
  message: String,
  cause: Throwable? = null,
) : SkillBillRuntimeException(message, cause) {
  class Absent(identity: String) : RejectedOutputDiagnosticError("Rejected output diagnostic '$identity' is absent.")
  class Expired(identity: String) : RejectedOutputDiagnosticError("Rejected output diagnostic '$identity' has expired.")
  class Oversized(
    identity: String,
  ) : RejectedOutputDiagnosticError("Rejected output diagnostic '$identity' is oversized.")
  class Corrupt(identity: String, cause: Throwable? = null) :
    RejectedOutputDiagnosticError("Rejected output diagnostic '$identity' is corrupt.", cause)
  class Permission(operation: String, cause: Throwable? = null) :
    RejectedOutputDiagnosticError("Rejected output diagnostic permission operation '$operation' failed.", cause)
  class Persistence(operation: String, cause: Throwable? = null) :
    RejectedOutputDiagnosticError("Rejected output diagnostic persistence operation '$operation' failed.", cause)
  class Retrieval(
    reason: String,
  ) : RejectedOutputDiagnosticError("Rejected output diagnostic retrieval failed: $reason")
  class InvalidRequest(reason: String) :
    RejectedOutputDiagnosticError("Rejected output diagnostic request is invalid: $reason")
  class InvalidConfiguration(reason: String) :
    RejectedOutputDiagnosticError("Rejected output diagnostic configuration is invalid: $reason")
  class Conflict(identity: String) :
    RejectedOutputDiagnosticError("Rejected output diagnostic '$identity' conflicts with immutable evidence.")
}
