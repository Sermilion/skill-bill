package dev.skillbill.runtime.buildlogic

val runtimeTargetTokens: List<String> =
  listOf(
    "macos-arm64",
    "macos-x64",
    "windows-x64",
    "linux-x64",
  )

fun resolveHostRuntimeToken(
  osName: String = System.getProperty("os.name").orEmpty(),
  osArch: String = System.getProperty("os.arch").orEmpty(),
): String? {
  val osFamily = resolveOsFamily(osName.lowercase())
  val arch = resolveArch(osArch.lowercase())
  if (osFamily == null || arch == null) return null
  return "$osFamily-$arch".takeIf { it in runtimeTargetTokens }
}

private fun resolveOsFamily(normalizedName: String): String? =
  when {
    normalizedName.contains("mac") || normalizedName.contains("darwin") -> "macos"
    normalizedName.contains("windows") -> "windows"
    normalizedName.contains("linux") -> "linux"
    else -> null
  }

private fun resolveArch(normalizedArch: String): String? =
  when {
    normalizedArch.contains("aarch64") || normalizedArch.contains("arm64") -> "arm64"
    normalizedArch.contains("x86_64") || normalizedArch.contains("amd64") -> "x64"
    else -> null
  }
