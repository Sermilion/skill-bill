package dev.skillbill.runtime.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

internal fun Project.configureQuality() {
  configure<SpotlessExtension> {
    kotlin {
      target("src/**/*.kt")
      ktlint()
      trimTrailingWhitespace()
      endWithNewline()
    }

    kotlinGradle {
      target("*.gradle.kts")
      ktlint()
      trimTrailingWhitespace()
      endWithNewline()
    }
  }

  configure<DetektExtension> {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    parallel = true
    basePath = rootDir.absolutePath
  }

  tasks.withType<Detekt>().configureEach {
    exclude("**/build/**", "**/generated/**")
    reports {
      html.required.set(false)
      xml.required.set(true)
      txt.required.set(false)
      sarif.required.set(false)
    }
  }
}
