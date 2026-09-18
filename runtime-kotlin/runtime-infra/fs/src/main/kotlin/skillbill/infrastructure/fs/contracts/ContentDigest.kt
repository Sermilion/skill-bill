package skillbill.infrastructure.fs.contracts

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal fun newSha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

internal fun sha256Bytes(bytes: ByteArray): ByteArray = newSha256Digest().digest(bytes)

internal fun sha256Hex(bytes: ByteArray): String = sha256Bytes(bytes).joinToString("") { byte -> "%02x".format(byte) }

internal fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

internal fun sha256HexOfFile(path: Path): String = sha256Hex(Files.readAllBytes(path))
