import dev.skillbill.runtime.buildlogic.RuntimeImageExtension
import dev.skillbill.runtime.buildlogic.RuntimeImageLicense
import dev.skillbill.runtime.buildlogic.configureStartScriptJavaGuard
import dev.skillbill.runtime.buildlogic.writeSha256Sidecar
import org.beryx.runtime.BaseTask
import org.beryx.runtime.data.RuntimePluginExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import java.io.File
import java.nio.file.Path

class RuntimeImageConventionPlugin : Plugin<Project> {
  private companion object {
    const val LINK_JDK_VERSION = 21

    val IMAGE_MODULES =
      listOf(
        "java.base",
        "java.logging",
        "java.management",
        "java.naming",
        "java.net.http",
        "java.sql",
        "java.xml",
        "java.desktop",
        "jdk.httpserver",
        "jdk.crypto.ec",
        "jdk.unsupported",
      )

    const val CC_OPT_OUT_REASON =
      "Badass Runtime is not configuration-cache compatible (serializes Gradle model objects)."

    const val UNSUPPORTED_HOST_SEGMENT = "unsupported-host"
  }

  override fun apply(target: Project) {
    with(target) {
      pluginManager.apply("org.beryx.runtime")

      val extension = extensions.create("runtimeImage", RuntimeImageExtension::class.java)
      val hostRuntimeToken = extension.hostRuntimeToken

      logUnsupportedHost(hostRuntimeToken)
      configureStartScriptJavaGuard()
      configureStaticRuntimeWiring(hostRuntimeToken)

      afterEvaluate {
        val baseName = extension.imageBaseName.get()
        val zipName = imageZipName(baseName, project.version.toString(), hostRuntimeToken)
        configureRuntimeImageZip(zipName)
        val licenseStageTask = registerRuntimeLicenseStaging(baseName)
        val licenseVerificationTask = registerRuntimeLicenseVerification(baseName, licenseStageTask)
        val sha256Task = registerSha256Task(baseName, zipName)
        configureRuntimeZipTask(baseName, hostRuntimeToken, licenseVerificationTask, sha256Task)
      }
    }
  }

  private fun Project.logUnsupportedHost(hostRuntimeToken: String?) {
    if (hostRuntimeToken != null) return

    logger.lifecycle(
      "SKILL-55: host os.name='${System.getProperty("os.name")}' " +
        "os.arch='${System.getProperty("os.arch")}' is not a supported runtime-image " +
        "target (known gap). Image tasks (runtimeZip/runtimeZipSha256) are unavailable " +
        "on this host; build them on a matching CI runner.",
    )
  }

  private fun Project.configureStaticRuntimeWiring(hostRuntimeToken: String?) {
    val toolchains = extensions.getByType<JavaToolchainService>()
    val linkJavaHomeProvider: Provider<String> =
      toolchains
        .launcherFor { languageVersion.set(JavaLanguageVersion.of(LINK_JDK_VERSION)) }
        .map { it.metadata.installationPath.asFile.absolutePath }

    extensions.configure<RuntimePluginExtension>("runtime") {
      javaHome.set(linkJavaHomeProvider)

      addOptions("--no-header-files", "--no-man-pages", "--strip-debug", "--compress", "2")

      additive.set(true)
      modules.set(IMAGE_MODULES)
    }

    tasks.withType<BaseTask>().configureEach {
      notCompatibleWithConfigurationCache(CC_OPT_OUT_REASON)
      if (hostRuntimeToken == null) {
        doFirst {
          error(unsupportedHostMessage())
        }
      }
    }
  }

  private fun Project.configureRuntimeImageZip(zipName: String) {
    extensions.configure<RuntimePluginExtension>("runtime") {
      imageZip.set(layout.buildDirectory.file("runtime-image/$zipName"))
    }
  }

  private fun imageZipName(baseName: String, version: String, hostRuntimeToken: String?): String {
    val tokenSegment = hostRuntimeToken ?: UNSUPPORTED_HOST_SEGMENT
    return "$baseName-$version-$tokenSegment.zip"
  }

  private fun Project.registerSha256Task(baseName: String, zipName: String): TaskProvider<*> {
    val runtimeImageZipPath =
      layout.buildDirectory
        .file("runtime-image/$zipName")
        .get()
        .asFile.absolutePath
    return tasks.register("runtimeZipSha256") {
      group = "distribution"
      description = "Write a SHA-256 sidecar next to the $baseName image zip (AC2)."
      dependsOn("runtimeZip")
      val archivePath = runtimeImageZipPath
      val checksumPath = "$archivePath.sha256"
      inputs.file(archivePath)
      outputs.file(checksumPath)
      notCompatibleWithConfigurationCache("Sidecar follows the not-cacheable runtimeZip task.")
      doLast {
        writeSha256Sidecar(File(archivePath))
      }
    }
  }

  private fun Project.registerRuntimeLicenseStaging(baseName: String): TaskProvider<*> {
    val rootLicensePath = rootProject.projectDir.parentFile.resolve("LICENSE").absolutePath
    val imageLicensePath = layout.buildDirectory.file("image/LICENSE").get().asFile.absolutePath
    val installLicensePath = layout.buildDirectory.file("install/$baseName/LICENSE").get().asFile.absolutePath
    return tasks.register("stageRuntimeLicense") {
      group = "distribution"
      description = "Stage the repository LICENSE in the $baseName install and runtime images."
      dependsOn("runtime")
      inputs.file(rootLicensePath)
      outputs.files(imageLicensePath, installLicensePath)
      doLast {
        val source = Path.of(rootLicensePath)
        try {
          RuntimeImageLicense.stage(source, listOf(Path.of(imageLicensePath), Path.of(installLicensePath)))
        } catch (error: IllegalArgumentException) {
          throw GradleException(error.message.orEmpty(), error)
        }
      }
    }
  }

  private fun Project.registerRuntimeLicenseVerification(
    baseName: String,
    licenseStageTask: TaskProvider<*>,
  ): TaskProvider<*> {
    val rootLicensePath = rootProject.projectDir.parentFile.resolve("LICENSE").absolutePath
    val imageLicensePath = layout.buildDirectory.file("image/LICENSE").get().asFile.absolutePath
    val installLicensePath = layout.buildDirectory.file("install/$baseName/LICENSE").get().asFile.absolutePath
    return tasks.register("verifyRuntimeImageLicense") {
      group = "verification"
      description = "Verify $baseName install and runtime images carry the root LICENSE unchanged."
      dependsOn(licenseStageTask)
      inputs.file(rootLicensePath)
      inputs.files(imageLicensePath, installLicensePath)
      doLast {
        val source = Path.of(rootLicensePath)
        listOf(Path.of(imageLicensePath), Path.of(installLicensePath)).forEach { destination ->
          if (!RuntimeImageLicense.matches(source, destination)) {
            throw GradleException("Packaged LICENSE differs from repository LICENSE at $destination.")
          }
        }
      }
    }
  }

  private fun Project.configureRuntimeZipTask(
    baseName: String,
    hostRuntimeToken: String?,
    licenseVerificationTask: TaskProvider<*>,
    sha256Task: TaskProvider<*>,
  ) {
    val tokenSegment = hostRuntimeToken ?: UNSUPPORTED_HOST_SEGMENT
    tasks.named("runtimeZip") {
      group = "distribution"
      description =
        "Build the self-contained $baseName image and a versioned image zip ($tokenSegment)."
      dependsOn(licenseVerificationTask)
      finalizedBy(sha256Task)
    }
  }

  private fun unsupportedHostMessage(): String = "SKILL-55: cannot build a self-contained runtime image on this host " +
    "(os.name='${System.getProperty("os.name")}', os.arch='${System.getProperty("os.arch")}'). " +
    "This is a known-gap target; build on a matching CI runner."
}
