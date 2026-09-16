package dev.skillbill.runtime.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DevSnapshotVersionTest {
  @Test
  fun `next snapshot follows the newest listed stable tag not an older ancestor`() {
    assertEquals(
      "0.4.2-SNAPSHOT",
      nextDevSnapshotVersion(listOf("v0.4.0", "v0.4.1", "v0.5.0-rc.1", "plugin-v9.9.9")),
    )
    assertEquals("0.4.1-SNAPSHOT", nextDevSnapshotVersion(listOf("v0.4.0")))
    assertEquals(null, nextDevSnapshotVersion(listOf("v0.5.0-rc.1")))
  }

  @Test
  fun `release version wins over listed tags`() {
    assertEquals("0.4.1", resolveSkillBillVersion("0.4.1", listOf("v0.4.0")))
    assertEquals("0.4.2-SNAPSHOT", resolveSkillBillVersion("  ", listOf("v0.4.1")))
    assertEquals("0.0.0-SNAPSHOT", resolveSkillBillVersion(null, emptyList()))
  }
}
