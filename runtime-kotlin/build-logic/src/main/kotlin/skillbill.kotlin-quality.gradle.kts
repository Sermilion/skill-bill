plugins {
  id("com.diffplug.spotless")
  id("io.gitlab.arturbosch.detekt")
}

spotless {
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

detekt {
  config.setFrom(rootProject.file("detekt.yml"))
  buildUponDefaultConfig = true
  allRules = false
  parallel = true
}

tasks.named("check") {
  dependsOn("spotlessCheck")
}
