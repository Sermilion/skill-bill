package dev.skillbill.runtime.buildlogic

import java.io.File
import java.security.MessageDigest

const val SHA256_BUFFER_BYTES: Int = 64 * 1024

fun sha256Hex(file: File): String {
  val digest = MessageDigest.getInstance("SHA-256")
  file.inputStream().use { stream ->
    val buffer = ByteArray(SHA256_BUFFER_BYTES)
    while (true) {
      val read = stream.read(buffer)
      if (read < 0) break
      digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { byteValue -> "%02x".format(byteValue) }
}

fun writeSha256Sidecar(archive: File): File {
  val hex = sha256Hex(archive)
  val sidecar = File(archive.parentFile, "${archive.name}.sha256")
  sidecar.writeText("$hex  ${archive.name}\n")
  return sidecar
}
