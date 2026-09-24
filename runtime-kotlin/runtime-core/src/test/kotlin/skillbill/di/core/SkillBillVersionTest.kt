package skillbill.di.core

import skillbill.goalrunner.model.DurableDecodeSubstitutionObservations
import skillbill.goalrunner.model.DurableDecodeSubstitutionRecord
import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class SkillBillVersionTest {
  @Test
  fun `missing version resource records the substituted value and expected source`() {
    DurableDecodeSubstitutionObservations.drain()

    val value = resourceVersion { null }
    val record = DurableDecodeSubstitutionObservations.drain().single()

    assertEquals("0.0.0-unknown", value)
    assertEquals(
      DurableDecodeSubstitutionRecord(
        seam = "SkillBillVersion.resourceVersion",
        valueUsed = "0.0.0-unknown",
        expectedValue = "packaged_version_property",
        reason = "missing_or_blank_version_resource",
      ),
      record,
    )
  }

  @Test
  fun `version resource value is returned without a substitution`() {
    DurableDecodeSubstitutionObservations.drain()

    val value =
      resourceVersion {
        ByteArrayInputStream("version=1.2.3".toByteArray())
      }

    assertEquals("1.2.3", value)
    assertEquals(emptyList(), DurableDecodeSubstitutionObservations.drain())
  }
}
