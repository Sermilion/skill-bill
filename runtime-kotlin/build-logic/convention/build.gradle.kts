import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  alias(libs.plugins.spotless)
  alias(libs.plugins.detekt)
}

group = "dev.skillbill.runtime.buildlogic"

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

dependencies {
  compileOnly(libs.kotlin.gradle.plugin)
  compileOnly(libs.spotless.gradle.plugin)
  compileOnly(libs.detekt.gradle.plugin)

  implementation(libs.beryx.runtime.gradle.plugin)

  testImplementation(libs.junit.jupiter)

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}

val ccOptOutReason =
  "Kotlin 2.4.0-Beta2 test-compile is not configuration-cache compatible under Gradle 9.3 " +
    "(serializes the Kotlin daemon error-file/profile as a File where a Property is expected)."
tasks.named("compileTestKotlin") { notCompatibleWithConfigurationCache(ccOptOutReason) }
tasks.withType<Test>().configureEach { notCompatibleWithConfigurationCache(ccOptOutReason) }

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint().editorConfigOverride(
      mapOf(
        "indent_size" to 2,
        "ij_kotlin_allow_trailing_comma" to true,
        "ij_kotlin_allow_trailing_comma_on_call_site" to true,
        "max_line_length" to 120,
      ),
    )
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
  config.setFrom(file("$rootDir/../config/detekt/detekt.yml"))
  buildUponDefaultConfig = true
  parallel = true
  basePath = rootDir.absolutePath
}

tasks.named("check") {
  dependsOn("spotlessCheck")
}

tasks {
  validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
  }
}

gradlePlugin {
  plugins {
    register("jvmLibrary") {
      id = "skillbill.jvm-library"
      implementationClass = "JvmLibraryConventionPlugin"
    }
    register("quality") {
      id = "skillbill.quality"
      implementationClass = "QualityConventionPlugin"
    }
    register("runtimeImage") {
      id = "skillbill.runtime-image"
      implementationClass = "RuntimeImageConventionPlugin"
    }
  }
}
