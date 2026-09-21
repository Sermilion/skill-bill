package skillbill.infrastructure.host.experiment.codegraph

object CodeGraphHostPlatform {
  fun currentPlatformId(): String? {
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val osArch = System.getProperty("os.arch").orEmpty().lowercase()
    val osFamily = when {
      osName.contains("mac") || osName.contains("darwin") -> "darwin"
      osName.contains("windows") -> "win32"
      osName.contains("linux") -> "linux"
      else -> return null
    }
    val arch = when {
      osArch.contains("aarch64") || osArch.contains("arm64") -> "arm64"
      osArch.contains("x86_64") || osArch.contains("amd64") -> "x64"
      else -> return null
    }
    return "$osFamily-$arch"
  }
}
