package skillbill.contracts.telemetry

object TelemetryProxyPayloadKeys {
  const val EVENT_DEDUPLICATION_ID: String = "\$insert_id"
  const val SUPPORTS_EVENT_DEDUPLICATION: String = "supports_event_deduplication"
  const val WORKFLOW: String = "workflow"
  const val DATE_FROM: String = "date_from"
  const val DATE_TO: String = "date_to"
  const val GROUP_BY: String = "group_by"
  const val SINCE: String = "since"
  const val PROXY_URL: String = "proxy_url"
  const val CAPABILITIES_URL: String = "capabilities_url"
  const val SOURCE: String = "source"
  const val SUPPORTS_INGEST: String = "supports_ingest"
  const val SUPPORTS_STATS: String = "supports_stats"
  const val SUPPORTED_WORKFLOWS: String = "supported_workflows"
}
