package skillbill.application.telemetry.service
import skillbill.application.telemetry.telemetry.service

fun prDescriptionWasEditedByUser(generatedDescription: String?, finalPrBody: String?): Boolean {
  val generated = generatedDescription?.normalizedPrDescriptionBody() ?: return false
  val actual = finalPrBody?.normalizedPrDescriptionBody() ?: return false
  return generated != actual
}

private fun String.normalizedPrDescriptionBody(): String = trim()
  .replace("\r\n", "\n")
  .replace('\r', '\n')
  .lines()
  .joinToString("\n") { it.trimEnd() }
