package dev.skillbill.runtime.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files

private const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

class Sha256SidecarTest {
  @Test
  fun `sidecar carries lowercase hex two spaces the archive name and a trailing newline`() {
    val archive = Files.createTempDirectory("skillbill-sidecar")
      .resolve("runtime-cli-1.2.3-macos-arm64.zip")
    Files.createFile(archive)

    assertEquals(
      "$EMPTY_SHA256  runtime-cli-1.2.3-macos-arm64.zip\n",
      sha256SidecarLine(archive.toFile()),
      "install.sh verify_sha256 parses this exact shape; drift breaks release-asset verification.",
    )
  }
}
