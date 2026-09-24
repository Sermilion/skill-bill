package skillbill.cli.scaffold.payload
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import skillbill.application.scaffold.decodeScaffoldPayloadObject
import skillbill.cli.kernel.cli.CliRunState
import skillbill.cli.scaffold.commands.NewAddonPayloadArgs
import java.nio.file.Path

internal fun createAndFillContentPayload(
  body: String?,
  bodyFile: String?,
  state: CliRunState,
): JsonObject {
  val contentBody =
    body ?: bodyFile?.let { path ->
      readCliTextFile(path, state)
    }
  return contentBody?.let { buildJsonObject { put("content_body", it) } } ?: JsonObject(emptyMap())
}

internal fun createAndFillScaffoldPayload(
  scaffoldPayload: JsonObject,
  body: String?,
  bodyFile: String?,
  state: CliRunState,
): JsonObject {
  val kind = scaffoldPayload["kind"]?.jsonPrimitive?.contentOrNull.orEmpty()
  require(kind !in setOf("platform-pack", "add-on")) {
    "create-and-fill can only scaffold one content-managed skill; kind '$kind' is not supported."
  }
  return JsonObject(scaffoldPayload + createAndFillContentPayload(body, bodyFile, state))
}

internal fun newAddonPayload(args: NewAddonPayloadArgs): Map<String, Any> =
  buildMap {
    put("scaffold_payload_version", "1.0")
    put("kind", "add-on")
    put("platform", args.platform.orEmpty())
    put("name", args.name.orEmpty())
    (args.body ?: args.bodyFile?.let { path -> readCliTextFile(path, args.state) })
      ?.let { addonBody -> put("body", addonBody) }
    args.addonLocationPath?.takeIf { it.isNotBlank() }?.let { path -> put("addon_location_path", path) }
    if (args.consumerSkillDirs.isNotEmpty()) {
      put("consumer_skill_dirs", args.consumerSkillDirs)
    }
  }

internal fun readCliTextFile(
  path: String,
  state: CliRunState,
): String = if (path == "-") state.wholeStdinText() else Path.of(path).toFile().readText()

internal fun readScaffoldPayload(
  payloadPath: String?,
  state: CliRunState,
): JsonObject = decodeScaffoldPayloadObject(readScaffoldPayloadText(payloadPath, state))

internal fun readScaffoldPayloadText(
  payloadPath: String?,
  state: CliRunState,
): String =
  when {
    payloadPath == null -> throw IllegalArgumentException("--payload is required for this command.")
    payloadPath == "-" -> state.wholeStdinText()
    else -> Path.of(payloadPath).toFile().readText()
  }

internal fun Any?.orEmpty(): String = this as? String ?: ""
