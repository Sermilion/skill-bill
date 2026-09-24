package skillbill.infrastructure.launcher.process.support

import java.security.MessageDigest

internal fun newLauncherSha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

internal fun launcherSha256Hex(bytes: ByteArray): String =
  newLauncherSha256Digest().digest(bytes).joinToString("") { byte -> "%02x".format(byte) }

internal fun launcherSha256Hex(text: String): String = launcherSha256Hex(text.toByteArray(Charsets.UTF_8))
