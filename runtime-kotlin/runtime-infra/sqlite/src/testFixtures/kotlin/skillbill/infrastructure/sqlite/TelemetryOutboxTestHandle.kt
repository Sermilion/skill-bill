package skillbill.infrastructure.sqlite

import skillbill.infrastructure.sqlite.telemetry.outbox.TelemetryOutboxStore
import skillbill.infrastructure.sqlite.telemetry.redaction.SkillBillRuntimeVersion
import skillbill.ports.telemetry.model.TelemetryOutboxRecord
import skillbill.ports.telemetry.transport.TelemetryOutboxRepository
import java.sql.Connection
class TelemetryOutboxTestHandle internal constructor(
  private val store: TelemetryOutboxStore,
) : TelemetryOutboxRepository by store {
  fun listPending(limit: Int? = null): List<TelemetryOutboxRecord> = store.listPending(limit)

  fun markSynced(id: Long, syncedAt: String) {
    store.markSynced(id, syncedAt)
  }
}

fun telemetryOutboxOnConnection(
  connection: Connection,
  version: String = SkillBillRuntimeVersion.VALUE,
): TelemetryOutboxTestHandle = TelemetryOutboxTestHandle(TelemetryOutboxStore(connection, version))
