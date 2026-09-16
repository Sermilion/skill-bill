import java.sql.*;
import java.time.Instant;
import java.util.List;
import skillbill.infrastructure.sqlite.telemetry.TelemetryOutboxStore;
import skillbill.ports.telemetry.model.TelemetryOutboxClaimRequest;

public class OutboxOwnershipProbe {
  public static void main(String[] args) throws Exception {
    try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:")) {
      c.createStatement().execute("CREATE TABLE telemetry_outbox (id INTEGER PRIMARY KEY, event_name TEXT, payload_json TEXT, created_at TEXT DEFAULT CURRENT_TIMESTAMP, synced_at TEXT, last_error TEXT, skill_bill_version TEXT, event_uuid TEXT, delivery_attempts INTEGER DEFAULT 0, claim_token TEXT, claimed_at TEXT)");
      var store = new TelemetryOutboxStore(c, "investigation");
      long id = store.enqueue("probe", "{}");
      Instant t = Instant.parse("2026-09-15T10:00:00Z");
      var a = store.claimPending(new TelemetryOutboxClaimRequest("A", 1, t, t.minusSeconds(300), 5));
      var b = store.claimPending(new TelemetryOutboxClaimRequest("B", 1, t.plusSeconds(301), t.plusSeconds(1), 5));
      System.out.println("A_claimed=" + a.size() + ", B_reclaimed=" + b.size());
      store.markUnconfirmed(List.of(id), "late sender A");
      try (var rows = c.createStatement().executeQuery("SELECT claim_token, last_error FROM telemetry_outbox")) {
        rows.next();
        System.out.println("B_claim_after_stale_A_settlement=" + rows.getString(1));
        System.out.println("last_error=" + rows.getString(2));
      }
      var third = store.claimPending(new TelemetryOutboxClaimRequest("C", 1, t.plusSeconds(302), t.plusSeconds(2), 5));
      System.out.println("C_claimed_while_B_in_flight=" + third.size());
    }
  }
}
