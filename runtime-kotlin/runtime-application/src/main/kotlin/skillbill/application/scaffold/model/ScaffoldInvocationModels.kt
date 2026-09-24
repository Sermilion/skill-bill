package skillbill.application.scaffold.model

import kotlinx.serialization.json.JsonObject
import skillbill.application.install.ExternalAddonOverlayService
import skillbill.scaffold.model.ScaffoldResult
import java.nio.file.Path
import java.time.Clock

data class ScaffoldInvocationArgs(
  val payloadText: String? = null,
  val payload: JsonObject? = null,
  val invocationRepositoryRoot: Path,
  val dryRun: Boolean,
  val registerExternalSources: Boolean,
  val externalAddonOverlayService: ExternalAddonOverlayService? = null,
  val userHome: Path,
  val environment: Map<String, String>,
  val clock: Clock,
)

data class ScaffoldInvocationOutcome(
  val sessionId: String,
  val scaffoldResult: ScaffoldResult,
  val registrationFailure: String? = null,
)
