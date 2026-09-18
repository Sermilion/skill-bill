package skillbill.infrastructure.contracts

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

fun newSha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

fun sha256Bytes(bytes: ByteArray): ByteArray = newSha256Digest().digest(bytes)

fun sha256Hex(bytes: ByteArray): String = sha256Bytes(bytes).joinToString("") { byte -> "%02x".format(byte) }

fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

fun sha256HexOfFile(path: Path): String = sha256Hex(Files.readAllBytes(path))
