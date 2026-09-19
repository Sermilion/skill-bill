package skillbill.di.core
import skillbill.goalrunner.DurableDecodeSubstitutionObservations
import java.io.InputStream
import java.util.Properties

object SkillBillVersion {
  val VALUE: String = resourceVersion {
    SkillBillVersion::class.java.classLoader.getResourceAsStream("skillbill/version.properties")
  }
}

internal fun resourceVersion(load: () -> InputStream?): String {
  val loaded = load()
    ?.use { stream ->
      Properties()
        .apply { load(stream) }
        .getProperty("version")
        ?.ifBlank { null }
    }
  if (loaded != null) {
    return loaded
  }
  DurableDecodeSubstitutionObservations.record(
    seam = "SkillBillVersion.resourceVersion",
    valueUsed = "0.0.0-unknown",
    expectedValue = "packaged_version_property",
    reason = "missing_or_blank_version_resource",
  )
  return "0.0.0-unknown"
}
