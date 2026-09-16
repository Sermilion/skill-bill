package skillbill.infrastructure.http

import me.tatarka.inject.annotations.Inject
import skillbill.ports.process.InstallerScriptFetchPort
import skillbill.ports.process.model.InstallerScriptFetchRequest
import skillbill.ports.process.model.InstallerScriptFetchResult
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists

@Inject
class HttpInstallerScriptFetchAdapter : InstallerScriptFetchPort {
  private val httpClient: HttpClient =
    HttpClient
      .newBuilder()
      .connectTimeout(DEFAULT_HTTP_CONNECT_TIMEOUT)
      .followRedirects(HttpClient.Redirect.NORMAL)
      .build()

  override fun fetch(request: InstallerScriptFetchRequest): InstallerScriptFetchResult {
    val stagingDirectory =
      try {
        Files.createTempDirectory("skill-bill-update-")
      } catch (error: IOException) {
        return InstallerScriptFetchResult.Failed(error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() })
      }
    val partialPath = stagingDirectory.resolve("install.sh.partial")
    val scriptPath = stagingDirectory.resolve("install.sh")
    return try {
      val httpRequest =
        HttpRequest
          .newBuilder(URI.create(request.url))
          .timeout(DEFAULT_HTTP_REQUEST_TIMEOUT)
          .header("User-Agent", USER_AGENT)
          .GET()
          .build()
      val response = httpClient.send(httpRequest, BodyHandlers.ofByteArray())
      if (response.statusCode() !in HTTP_SUCCESS_RANGE) {
        partialPath.deleteIfExists()
        runCatching { Files.deleteIfExists(stagingDirectory) }
        return InstallerScriptFetchResult.Failed(
          "install script download failed with HTTP ${response.statusCode()}",
        )
      }
      Files.write(partialPath, response.body())
      Files.move(partialPath, scriptPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      InstallerScriptFetchResult.Ready(scriptPath)
    } catch (interrupted: InterruptedException) {
      runCatching { partialPath.deleteIfExists() }
      runCatching { scriptPath.deleteIfExists() }
      runCatching { Files.deleteIfExists(stagingDirectory) }
      throw interrupted
    } catch (error: IOException) {
      runCatching { partialPath.deleteIfExists() }
      runCatching { scriptPath.deleteIfExists() }
      runCatching { Files.deleteIfExists(stagingDirectory) }
      InstallerScriptFetchResult.Failed(error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() })
    } catch (error: IllegalArgumentException) {
      runCatching { partialPath.deleteIfExists() }
      runCatching { scriptPath.deleteIfExists() }
      runCatching { Files.deleteIfExists(stagingDirectory) }
      InstallerScriptFetchResult.Failed(error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() })
    }
  }

  override fun cleanup(scriptPath: Path) {
    val stagingDirectory = scriptPath.parent ?: return
    runCatching {
      Files.walk(stagingDirectory).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { path -> Files.deleteIfExists(path) }
      }
    }
  }

  companion object {
    private const val USER_AGENT = "skill-bill-update"
    private val HTTP_SUCCESS_RANGE = 200..299
  }
}
