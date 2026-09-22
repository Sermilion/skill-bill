package dev.skillbill.runtime.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.kotlin.dsl.withType

private const val GUARD_ANCHOR = "# Determine the Java command to use to start the JVM."

private const val GUARD_MARKER = "skill_bill_required_java_major="

private const val EMBEDDED_JRE_MARKER = "JAVA_HOME=\"\$APP_HOME\""

internal fun startScriptWithJavaGuard(
  generated: String,
  guard: String,
  scriptName: String,
): String {
  if (generated.contains(GUARD_MARKER)) return generated
  if (generated.contains(GUARD_ANCHOR)) return generated.replace(GUARD_ANCHOR, guard + GUARD_ANCHOR)
  if (generated.contains(EMBEDDED_JRE_MARKER)) return generated
  throw GradleException(
    "Start-script Java guard anchor is absent from $scriptName. Gradle changed the " +
      "generated launcher; update GUARD_ANCHOR in StartScriptJavaGuard.kt.",
  )
}

internal fun Project.configureStartScriptJavaGuard(guard: RegularFileProperty) {
  tasks.withType<CreateStartScripts>().configureEach {
    inputs.file(guard).withPropertyName("skillBillJavaGuard")
    doLast {
      val script = unixScript
      val generated = script.readText()
      val guarded = startScriptWithJavaGuard(generated, guard.get().asFile.readText(), script.name)
      if (guarded != generated) script.writeText(guarded)
    }
  }
}
