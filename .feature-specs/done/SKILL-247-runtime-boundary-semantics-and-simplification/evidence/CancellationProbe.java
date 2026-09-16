import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.concurrent.CancellationException;
import skillbill.application.telemetry.sync.TelemetrySyncRuntime;
import skillbill.telemetry.model.TelemetrySettings;
import skillbill.ports.telemetry.TelemetryOutboxRepository;
import skillbill.ports.telemetry.TelemetryClient;

public class CancellationProbe {
  public static void main(String[] args) throws Exception {
    var ctor = java.util.Arrays.stream(TelemetrySettings.class.getDeclaredConstructors())
      .filter(c -> c.getParameterCount() == 7).findFirst().orElseThrow();
    ctor.setAccessible(true);
    var settings = (TelemetrySettings) ctor.newInstance("/tmp/probe.yaml", "full", true, "probe", "http://unused.invalid", null, 1);
    var repository = (TelemetryOutboxRepository) Proxy.newProxyInstance(
      CancellationProbe.class.getClassLoader(), new Class<?>[]{TelemetryOutboxRepository.class},
      (p, m, a) -> { throw new CancellationException("probe-cancelled"); });
    var client = (TelemetryClient) Proxy.newProxyInstance(
      CancellationProbe.class.getClassLoader(), new Class<?>[]{TelemetryClient.class},
      (p, m, a) -> { throw new AssertionError("network must not be called"); });
    var result = TelemetrySyncRuntime.INSTANCE.autoSyncTelemetry(settings, repository, client, Instant.EPOCH);
    System.out.println("auto_sync_result_after_cancellation=" + result);
    System.out.println("cancellation_propagated=false");
  }
}
