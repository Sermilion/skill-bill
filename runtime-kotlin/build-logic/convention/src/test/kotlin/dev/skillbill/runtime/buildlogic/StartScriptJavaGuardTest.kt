package dev.skillbill.runtime.buildlogic

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val ANCHOR = "# Determine the Java command to use to start the JVM."

private const val GUARD = "skill_bill_required_java_major=21\n"

class StartScriptJavaGuardTest {
  @Test
  fun `the guard is inserted once before the anchor and survives a regenerated script`() {
    val generated = "#!/bin/sh\n\n$ANCHOR\nJAVACMD=java\n"

    val patched = startScriptWithJavaGuard(generated, GUARD, "skill-bill")
    val repatched = startScriptWithJavaGuard(patched, GUARD, "skill-bill")

    assertEquals("#!/bin/sh\n\n$GUARD$ANCHOR\nJAVACMD=java\n", patched)
    assertEquals(
      1,
      Regex("skill_bill_required_java_major=").findAll(repatched).count(),
      "A second guard body lets the launcher resolve JAVA_HOME twice under conflicting rules.",
    )
  }

  @Test
  fun `a launcher without the guard anchor fails loudly`() {
    assertThrows<GradleException> {
      startScriptWithJavaGuard("#!/bin/sh\nexec java -jar app.jar\n", GUARD, "skill-bill")
    }
  }
}
