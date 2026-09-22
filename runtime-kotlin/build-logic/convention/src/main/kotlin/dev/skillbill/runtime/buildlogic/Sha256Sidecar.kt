package dev.skillbill.runtime.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.security.MessageDigest

private const val SHA256_BUFFER_BYTES: Int = 64 * 1024

fun sha256Hex(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.forEachBlock(SHA256_BUFFER_BYTES) { buffer, bytesRead -> digest.update(buffer, 0, bytesRead) }
  return digest.digest().joinToString("") { byteValue -> "%02x".format(byteValue) }
}

fun sha256SidecarLine(archive: File): String = "${sha256Hex(archive)}  ${archive.name}\n"

@DisableCachingByDefault(because = "Hashing one archive is cheaper than a build cache round trip.")
abstract class Sha256SidecarTask : DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.NONE)
  abstract val archive: RegularFileProperty

  @get:OutputFile
  abstract val sidecar: RegularFileProperty

  @TaskAction
  fun write() {
    sidecar.get().asFile.writeText(sha256SidecarLine(archive.get().asFile))
  }
}
