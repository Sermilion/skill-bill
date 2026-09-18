package skillbill.infrastructure.http

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

class JdkHttpRemoteTransportDeadlineTest {
  @Test
  fun `execute returns before the configured request deadline when the peer never responds`() {
    val executor = Executors.newSingleThreadExecutor()
    var server: HttpServer? = null
    try {
      val bound =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
          setExecutor(executor)
          createContext("/") { exchange ->
            try {
              Thread.sleep(TimeUnit.MINUTES.toMillis(10))
            } finally {
              exchange.close()
            }
          }
          start()
        }
      server = bound
      val transport =
        JdkHttpRemoteTransport.create(
          connectTimeout = Duration.ofMillis(500),
          requestTimeout = Duration.ofMillis(500),
        )
      val started = System.nanoTime()
      runCatching {
        transport.execute("GET", "http://127.0.0.1:${bound.address.port}/", null, emptyMap())
      }
      val elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
      assertTrue(elapsedMillis < TimeUnit.SECONDS.toMillis(5), "expected a deadline failure, elapsed=$elapsedMillis ms")
    } finally {
      server?.stop(0)
      executor.shutdownNow()
      executor.awaitTermination(5, TimeUnit.SECONDS)
    }
  }
}
