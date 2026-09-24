const DEFAULT_POSTHOG_INGEST_HOST = "https://us.i.posthog.com";
const DEFAULT_POSTHOG_APP_HOST = "https://us.posthog.com";
const MAX_BATCH_SIZE = 100;
const CONTRACT_VERSION = "3";
const RESERVED_TEST_INSTALL_ID = "test-install-id";
const MAX_EXCEPTION_FRAMES = 12;

const PRODUCTION_INSTALL_FILTER = `
      AND properties.install_id IS NOT NULL
      AND trim(toString(properties.install_id)) != ''
      AND toString(properties.install_id) != 'test-install-id'`;

function jsonResponse(status, payload) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function normalizeHost(host, fallback) {
  return (host || fallback).replace(/\/+$/, "");
}

function isValidEvent(event) {
  return (
    typeof event === "object" &&
    event !== null &&
    typeof event.event === "string" &&
    event.event.length > 0 &&
    typeof event.distinct_id === "string" &&
    event.distinct_id.length > 0 &&
    typeof event.properties === "object" &&
    event.properties !== null
  );
}

function installIdentity(event) {
  return String(event.properties?.install_id ?? event.distinct_id ?? "").trim();
}

function isProductionEvent(event) {
  const identity = installIdentity(event);
  return identity !== "" && identity !== RESERVED_TEST_INSTALL_ID && event.distinct_id !== RESERVED_TEST_INSTALL_ID;
}

function isIsoDate(value) {
  return /^\d{4}-\d{2}-\d{2}$/.test(value || "");
}

function escapeSqlLiteral(value) {
  return String(value).replace(/'/g, "''");
}

function nextIsoDate(value) {
  const next = new Date(`${value}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString().slice(0, 10);
}

function addDaysIsoDate(value, days) {
  const next = new Date(`${value}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + days);
  return next.toISOString().slice(0, 10);
}

function maxIsoDate(left, right) {
  return left > right ? left : right;
}

function minIsoDate(left, right) {
  return left < right ? left : right;
}

function weekStartIsoDate(value) {
  const current = new Date(`${value}T00:00:00Z`);
  const day = current.getUTCDay();
  const offset = day === 0 ? -6 : 1 - day;
  current.setUTCDate(current.getUTCDate() + offset);
  return current.toISOString().slice(0, 10);
}

function rate(numerator, denominator) {
  if (!denominator) {
    return 0;
  }
  return Math.round((numerator / denominator) * 1000) / 1000;
}

function average(value) {
  const numeric = Number(value || 0);
  if (!Number.isFinite(numeric)) {
    return 0;
  }
  return Math.round(numeric * 100) / 100;
}

function toInt(value) {
  const numeric = Number(value || 0);
  if (!Number.isFinite(numeric)) {
    return 0;
  }
  return Math.trunc(numeric);
}

function validateStatsRequest(payload) {
  if (typeof payload !== "object" || payload === null) {
    return "Request body must be a JSON object.";
  }
  if (payload.workflow !== "bill-feature-verify") {
    return "workflow must be one of: bill-feature-verify.";
  }
  if (!isIsoDate(payload.date_from) || !isIsoDate(payload.date_to)) {
    return "date_from and date_to must use YYYY-MM-DD format.";
  }
  if (payload.date_from > payload.date_to) {
    return "date_from must be on or before date_to.";
  }
  if (payload.group_by && !["day", "week"].includes(payload.group_by)) {
    return "group_by must be one of: day, week.";
  }
  return null;
}

function capabilitiesPayload(env) {
  const supportsIngest = Boolean(env.POSTHOG_API_KEY);
  const supportsStats = Boolean(env.POSTHOG_PERSONAL_API_KEY && env.POSTHOG_PROJECT_ID);
  return {
    contract_version: CONTRACT_VERSION,
    source: "remote_proxy",
    supports_ingest: supportsIngest,
    supports_stats: supportsStats,
    supported_workflows: supportsStats ? ["bill-feature-verify"] : [],
    stats_auth_required: Boolean(env.PROXY_STATS_BEARER_TOKEN),
    // PostHog keys event identity on the top-level `uuid`; `$insert_id` is only a legacy mirror it
    // ignores. transformBatch spreads every event, so the producer's `uuid` reaches PostHog unchanged
    // and a retried batch overwrites instead of duplicating. A fork that drops or rewrites `uuid`
    // must report false here, so the client refuses to send rather than degrade silently.
    supports_event_deduplication: true,
  };
}

async function readJson(request) {
  try {
    return await request.json();
  } catch {
    return null;
  }
}

function exceptionFrames(stackTrace) {
  const lines = Array.isArray(stackTrace) ? stackTrace : String(stackTrace || "").split("\n");
  return lines
    .map((line) => String(line).trim())
    .filter((line) => line !== "")
    .slice(0, MAX_EXCEPTION_FRAMES)
    .map((line) => ({ raw_id: line, function: line, platform: "java", in_app: line.includes("skillbill.") }));
}

function withoutGeoIp(event) {
  return { ...event, properties: { ...event.properties, $geoip_disable: true } };
}

function transformBatch(batch) {
  return batch.map((event) => {
    if (event.event !== "skillbill_runtime_exception") {
      return withoutGeoIp(event);
    }
    return withoutGeoIp({
      ...event,
      event: "$exception",
      properties: {
        ...event.properties,
        $exception_type: event.properties.error_type || "UnknownError",
        $exception_message: event.properties.error_message || "",
        $exception_list: [
          {
            type: event.properties.error_type || "UnknownError",
            value: event.properties.error_message || "",
            stacktrace: { type: "raw", frames: exceptionFrames(event.properties.stack_trace) },
          },
        ],
      },
    });
  });
}

function dropTestTraffic(batch) {
  const production = batch.filter(isProductionEvent);
  return { production, dropped: batch.length - production.length };
}

// Acknowledgement semantics: this relay acknowledges a batch only after PostHog has accepted it.
// The 10s abort and the 502 rewrite below are both lost-acknowledgement sources — the upstream may
// have accepted the batch before the abort fired or before the error surfaced — so the client must
// treat a timeout or a 502 from here as an unknown outcome, keep the rows pending with their
// original $insert_id, and let PostHog deduplicate the retry. Treating either as a rejection would
// discard delivered events; treating either as success would drop undelivered ones.
async function forwardBatch(env, batch) {
  if (!env.POSTHOG_API_KEY) {
    return jsonResponse(500, { error: "POSTHOG_API_KEY is not configured." });
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10_000);
  let upstreamResponse;
  try {
    upstreamResponse = await fetch(`${normalizeHost(env.POSTHOG_HOST, DEFAULT_POSTHOG_INGEST_HOST)}/batch/`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        api_key: env.POSTHOG_API_KEY,
        batch,
      }),
      signal: controller.signal,
    });
  } finally {
    clearTimeout(timer);
  }

  if (!upstreamResponse.ok) {
    return jsonResponse(502, { error: "Upstream telemetry relay returned an error." });
  }

  const responseText = await upstreamResponse.text();
  return new Response(
    responseText || JSON.stringify({ ok: true }),
    {
      status: upstreamResponse.status,
      headers: {
        "Content-Type": upstreamResponse.headers.get("Content-Type") || "application/json",
      },
    },
  );
}

async function runPostHogQuery(env, query) {
  if (!env.POSTHOG_PERSONAL_API_KEY) {
    return { error: "POSTHOG_PERSONAL_API_KEY is not configured.", status: 500 };
  }
  if (!env.POSTHOG_PROJECT_ID) {
    return { error: "POSTHOG_PROJECT_ID is not configured.", status: 500 };
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 10_000);
  let upstreamResponse;
  try {
    upstreamResponse = await fetch(
      `${normalizeHost(env.POSTHOG_APP_HOST, DEFAULT_POSTHOG_APP_HOST)}/api/projects/${env.POSTHOG_PROJECT_ID}/query/`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${env.POSTHOG_PERSONAL_API_KEY}`,
        },
        body: JSON.stringify({
          query: {
            kind: "HogQLQuery",
            query,
          },
        }),
        signal: controller.signal,
      },
    );
  } finally {
    clearTimeout(timer);
  }

  const responseText = await upstreamResponse.text();
  if (!upstreamResponse.ok) {
    return { error: "Upstream telemetry stats backend returned an error.", status: 502 };
  }

  let payload;
  try {
    payload = responseText ? JSON.parse(responseText) : {};
  } catch {
    return { error: "Upstream telemetry stats backend returned invalid JSON.", status: 502 };
  }
  return { payload, status: 200 };
}

const VERIFY_FINISHED_SESSION = "uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished'";
const KNOWN_COMPLETION_STATUSES = "('completed', 'abandoned_at_review', 'abandoned_at_audit', 'error', 'stale')";

const COMPLETION_STATUS_TAIL_COLUMNS = `
      ${VERIFY_FINISHED_SESSION} AND toString(properties.completion_status) = 'stale') AS completion_status_stale,
      ${VERIFY_FINISHED_SESSION} AND toString(properties.completion_status) NOT IN ${KNOWN_COMPLETION_STATUSES}) AS completion_status_other`
  .replace(/^\n/, "");

const HISTORY_SIGNALS = ["none", "irrelevant", "low", "medium", "high"];

const HISTORY_COLUMNS = [
  `      ${VERIFY_FINISHED_SESSION} AND (
        toString(properties.history_relevance) IN ('irrelevant', 'low', 'medium', 'high')
        OR toString(properties.history_helpfulness) IN ('irrelevant', 'low', 'medium', 'high')
      )) AS history_read_runs`,
  ...["relevance", "helpfulness"].flatMap((dimension) =>
    HISTORY_SIGNALS.map(
      (signal) =>
        `      ${VERIFY_FINISHED_SESSION} AND toString(properties.history_${dimension}) = '${signal}') AS history_${dimension}_${signal}`,
    ),
  ),
].join(",\n");

const MEASURED_COMPLETED_DURATION = `event = 'skillbill_feature_verify_finished'
        AND toString(properties.completion_status) = 'completed'
        AND properties.duration_seconds IS NOT NULL
        AND (properties.duration_seconds_availability IS NULL OR toString(properties.duration_seconds_availability) = 'measured')`;

function buildVerifyStatsQuery(dateFrom, dateToExclusive) {
  const from = escapeSqlLiteral(`${dateFrom} 00:00:00`);
  const to = escapeSqlLiteral(`${dateToExclusive} 00:00:00`);
  return `
    SELECT
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_started') AS started_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished') AS finished_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_started' AND lower(toString(properties.rollout_relevant)) = 'true') AS rollout_relevant_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND lower(toString(properties.feature_flag_audit_performed)) = 'true') AS feature_flag_audit_performed_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.audit_result) = 'all_pass') AS audit_result_all_pass,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.audit_result) = 'had_gaps') AS audit_result_had_gaps,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.audit_result) = 'skipped') AS audit_result_skipped,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'completed') AS completion_status_completed,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'abandoned_at_review') AS completion_status_abandoned_at_review,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'abandoned_at_audit') AS completion_status_abandoned_at_audit,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'error') AS completion_status_error,
${COMPLETION_STATUS_TAIL_COLUMNS},
${HISTORY_COLUMNS},
      avgIf(toFloatOrZero(toString(properties.acceptance_criteria_count)), event = 'skillbill_feature_verify_started') AS average_acceptance_criteria_count,
      avgIf(toFloatOrZero(toString(properties.review_iterations)), event = 'skillbill_feature_verify_finished') AS average_review_iterations,
      avgIf(toFloat(toString(properties.duration_seconds)), ${MEASURED_COMPLETED_DURATION}) AS average_duration_seconds
    FROM events
    WHERE event IN ('skillbill_feature_verify_started', 'skillbill_feature_verify_finished')
      AND timestamp >= toDateTime('${from}')
      AND timestamp < toDateTime('${to}')
${PRODUCTION_INSTALL_FILTER}
  `;
}

function buildVerifySeriesQuery(dateFrom, dateToExclusive) {
  const from = escapeSqlLiteral(`${dateFrom} 00:00:00`);
  const to = escapeSqlLiteral(`${dateToExclusive} 00:00:00`);
  return `
    SELECT
      toString(toDate(timestamp)) AS bucket_date,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_started') AS started_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished') AS finished_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_started' AND lower(toString(properties.rollout_relevant)) = 'true') AS rollout_relevant_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND lower(toString(properties.feature_flag_audit_performed)) = 'true') AS feature_flag_audit_performed_runs,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.audit_result) = 'all_pass') AS audit_result_all_pass,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.audit_result) = 'had_gaps') AS audit_result_had_gaps,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.audit_result) = 'skipped') AS audit_result_skipped,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'completed') AS completion_status_completed,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'abandoned_at_review') AS completion_status_abandoned_at_review,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'abandoned_at_audit') AS completion_status_abandoned_at_audit,
      uniqExactIf(toString(properties.session_id), event = 'skillbill_feature_verify_finished' AND toString(properties.completion_status) = 'error') AS completion_status_error,
${COMPLETION_STATUS_TAIL_COLUMNS},
${HISTORY_COLUMNS}
    FROM events
    WHERE event IN ('skillbill_feature_verify_started', 'skillbill_feature_verify_finished')
      AND timestamp >= toDateTime('${from}')
      AND timestamp < toDateTime('${to}')
${PRODUCTION_INSTALL_FILTER}
    GROUP BY bucket_date
    ORDER BY bucket_date
  `;
}

function rowToObject(columns, row) {
  const payload = {};
  columns.forEach((column, index) => {
    payload[column] = row[index];
  });
  return payload;
}

function queryRowsToObjects(payload) {
  const columns = Array.isArray(payload?.columns) ? payload.columns : [];
  const results = Array.isArray(payload?.results) ? payload.results : [];
  return results
    .filter((row) => Array.isArray(row))
    .map((row) => rowToObject(columns, row));
}

export function normalizeVerifyStats(row, dateFrom, dateTo) {
  const startedRuns = toInt(row.started_runs);
  const finishedRuns = toInt(row.finished_runs);
  const inProgressRuns = Math.max(startedRuns - finishedRuns, 0);
  const completedRuns = toInt(row.completion_status_completed);
  const abandonedAtReviewRuns = toInt(row.completion_status_abandoned_at_review);
  const abandonedAtAuditRuns = toInt(row.completion_status_abandoned_at_audit);
  const abandonedRuns = abandonedAtReviewRuns + abandonedAtAuditRuns;
  const historyRelevantRuns = toInt(row.history_relevance_medium) + toInt(row.history_relevance_high);
  const historyHelpfulRuns = toInt(row.history_helpfulness_medium) + toInt(row.history_helpfulness_high);
  return {
    status: "ok",
    source: "remote_proxy",
    workflow: "bill-feature-verify",
    date_from: dateFrom,
    date_to: dateTo,
    started_runs: startedRuns,
    finished_runs: finishedRuns,
    in_progress_runs: inProgressRuns,
    in_progress_rate: rate(inProgressRuns, startedRuns),
    finished_without_start_runs: Math.max(finishedRuns - startedRuns, 0),
    completion_rate: rate(completedRuns, startedRuns),
    abandonment_rate: rate(abandonedRuns, startedRuns),
    completion_status_counts: {
      completed: completedRuns,
      abandoned_at_review: abandonedAtReviewRuns,
      abandoned_at_audit: abandonedAtAuditRuns,
      error: toInt(row.completion_status_error),
      stale: toInt(row.completion_status_stale),
      other: toInt(row.completion_status_other),
    },
    audit_result_counts: {
      all_pass: toInt(row.audit_result_all_pass),
      had_gaps: toInt(row.audit_result_had_gaps),
      skipped: toInt(row.audit_result_skipped),
    },
    rollout_relevant_runs: toInt(row.rollout_relevant_runs),
    rollout_relevant_rate: rate(toInt(row.rollout_relevant_runs), startedRuns),
    feature_flag_audit_performed_runs: toInt(row.feature_flag_audit_performed_runs),
    feature_flag_audit_performed_rate: rate(toInt(row.feature_flag_audit_performed_runs), finishedRuns),
    history_read_runs: toInt(row.history_read_runs),
    history_read_rate: rate(toInt(row.history_read_runs), finishedRuns),
    history_relevant_runs: historyRelevantRuns,
    history_relevant_rate: rate(historyRelevantRuns, finishedRuns),
    history_helpful_runs: historyHelpfulRuns,
    history_helpful_rate: rate(historyHelpfulRuns, finishedRuns),
    history_relevance_counts: {
      none: toInt(row.history_relevance_none),
      irrelevant: toInt(row.history_relevance_irrelevant),
      low: toInt(row.history_relevance_low),
      medium: toInt(row.history_relevance_medium),
      high: toInt(row.history_relevance_high),
    },
    history_helpfulness_counts: {
      none: toInt(row.history_helpfulness_none),
      irrelevant: toInt(row.history_helpfulness_irrelevant),
      low: toInt(row.history_helpfulness_low),
      medium: toInt(row.history_helpfulness_medium),
      high: toInt(row.history_helpfulness_high),
    },
    average_acceptance_criteria_count: average(row.average_acceptance_criteria_count),
    average_review_iterations: average(row.average_review_iterations),
    average_duration_seconds: average(row.average_duration_seconds),
  };
}

export function normalizeVerifySeriesEntry(row, bucketStart, bucketEnd) {
  const startedRuns = toInt(row.started_runs);
  const finishedRuns = toInt(row.finished_runs);
  const inProgressRuns = Math.max(startedRuns - finishedRuns, 0);
  const completedRuns = toInt(row.completion_status_completed);
  const abandonedAtReviewRuns = toInt(row.completion_status_abandoned_at_review);
  const abandonedAtAuditRuns = toInt(row.completion_status_abandoned_at_audit);
  const abandonedRuns = abandonedAtReviewRuns + abandonedAtAuditRuns;
  const historyRelevantRuns = toInt(row.history_relevance_medium) + toInt(row.history_relevance_high);
  const historyHelpfulRuns = toInt(row.history_helpfulness_medium) + toInt(row.history_helpfulness_high);
  return {
    bucket_start: bucketStart,
    bucket_end: bucketEnd,
    started_runs: startedRuns,
    finished_runs: finishedRuns,
    in_progress_runs: inProgressRuns,
    in_progress_rate: rate(inProgressRuns, startedRuns),
    finished_without_start_runs: Math.max(finishedRuns - startedRuns, 0),
    completion_rate: rate(completedRuns, startedRuns),
    abandonment_rate: rate(abandonedRuns, startedRuns),
    completion_status_counts: {
      completed: completedRuns,
      abandoned_at_review: abandonedAtReviewRuns,
      abandoned_at_audit: abandonedAtAuditRuns,
      error: toInt(row.completion_status_error),
      stale: toInt(row.completion_status_stale),
      other: toInt(row.completion_status_other),
    },
    audit_result_counts: {
      all_pass: toInt(row.audit_result_all_pass),
      had_gaps: toInt(row.audit_result_had_gaps),
      skipped: toInt(row.audit_result_skipped),
    },
    rollout_relevant_runs: toInt(row.rollout_relevant_runs),
    rollout_relevant_rate: rate(toInt(row.rollout_relevant_runs), startedRuns),
    feature_flag_audit_performed_runs: toInt(row.feature_flag_audit_performed_runs),
    feature_flag_audit_performed_rate: rate(toInt(row.feature_flag_audit_performed_runs), finishedRuns),
    history_read_runs: toInt(row.history_read_runs),
    history_read_rate: rate(toInt(row.history_read_runs), finishedRuns),
    history_relevant_runs: historyRelevantRuns,
    history_relevant_rate: rate(historyRelevantRuns, finishedRuns),
    history_helpful_runs: historyHelpfulRuns,
    history_helpful_rate: rate(historyHelpfulRuns, finishedRuns),
    history_relevance_counts: {
      none: toInt(row.history_relevance_none),
      irrelevant: toInt(row.history_relevance_irrelevant),
      low: toInt(row.history_relevance_low),
      medium: toInt(row.history_relevance_medium),
      high: toInt(row.history_relevance_high),
    },
    history_helpfulness_counts: {
      none: toInt(row.history_helpfulness_none),
      irrelevant: toInt(row.history_helpfulness_irrelevant),
      low: toInt(row.history_helpfulness_low),
      medium: toInt(row.history_helpfulness_medium),
      high: toInt(row.history_helpfulness_high),
    },
  };
}

function summarizeVerifySeriesBucket(bucketStart, bucketEnd, entries) {
  const startedRuns = entries.reduce((sum, entry) => sum + toInt(entry.started_runs), 0);
  const finishedRuns = entries.reduce((sum, entry) => sum + toInt(entry.finished_runs), 0);
  const completedRuns = entries.reduce((sum, entry) => sum + toInt(entry.completion_status_counts?.completed), 0);
  const abandonedAtReviewRuns = entries.reduce((sum, entry) => sum + toInt(entry.completion_status_counts?.abandoned_at_review), 0);
  const abandonedAtAuditRuns = entries.reduce((sum, entry) => sum + toInt(entry.completion_status_counts?.abandoned_at_audit), 0);
  const abandonedRuns = abandonedAtReviewRuns + abandonedAtAuditRuns;
  const inProgressRuns = Math.max(startedRuns - finishedRuns, 0);
  const historyReadRuns = entries.reduce((sum, entry) => sum + toInt(entry.history_read_runs), 0);
  const historyRelevantRuns = entries.reduce((sum, entry) => sum + toInt(entry.history_relevant_runs), 0);
  const historyHelpfulRuns = entries.reduce((sum, entry) => sum + toInt(entry.history_helpful_runs), 0);
  return {
    bucket_start: bucketStart,
    bucket_end: bucketEnd,
    started_runs: startedRuns,
    finished_runs: finishedRuns,
    in_progress_runs: inProgressRuns,
    in_progress_rate: rate(inProgressRuns, startedRuns),
    finished_without_start_runs: Math.max(finishedRuns - startedRuns, 0),
    completion_rate: rate(completedRuns, startedRuns),
    abandonment_rate: rate(abandonedRuns, startedRuns),
    completion_status_counts: {
      completed: completedRuns,
      abandoned_at_review: abandonedAtReviewRuns,
      abandoned_at_audit: abandonedAtAuditRuns,
      error: entries.reduce((sum, entry) => sum + toInt(entry.completion_status_counts?.error), 0),
      stale: entries.reduce((sum, entry) => sum + toInt(entry.completion_status_counts?.stale), 0),
      other: entries.reduce((sum, entry) => sum + toInt(entry.completion_status_counts?.other), 0),
    },
    audit_result_counts: {
      all_pass: entries.reduce((sum, entry) => sum + toInt(entry.audit_result_counts?.all_pass), 0),
      had_gaps: entries.reduce((sum, entry) => sum + toInt(entry.audit_result_counts?.had_gaps), 0),
      skipped: entries.reduce((sum, entry) => sum + toInt(entry.audit_result_counts?.skipped), 0),
    },
    rollout_relevant_runs: entries.reduce((sum, entry) => sum + toInt(entry.rollout_relevant_runs), 0),
    rollout_relevant_rate: rate(
      entries.reduce((sum, entry) => sum + toInt(entry.rollout_relevant_runs), 0),
      startedRuns,
    ),
    feature_flag_audit_performed_runs: entries.reduce((sum, entry) => sum + toInt(entry.feature_flag_audit_performed_runs), 0),
    feature_flag_audit_performed_rate: rate(
      entries.reduce((sum, entry) => sum + toInt(entry.feature_flag_audit_performed_runs), 0),
      finishedRuns,
    ),
    history_read_runs: historyReadRuns,
    history_read_rate: rate(historyReadRuns, finishedRuns),
    history_relevant_runs: historyRelevantRuns,
    history_relevant_rate: rate(historyRelevantRuns, finishedRuns),
    history_helpful_runs: historyHelpfulRuns,
    history_helpful_rate: rate(historyHelpfulRuns, finishedRuns),
    history_relevance_counts: {
      none: entries.reduce((sum, entry) => sum + toInt(entry.history_relevance_counts?.none), 0),
      irrelevant: entries.reduce((sum, entry) => sum + toInt(entry.history_relevance_counts?.irrelevant), 0),
      low: entries.reduce((sum, entry) => sum + toInt(entry.history_relevance_counts?.low), 0),
      medium: entries.reduce((sum, entry) => sum + toInt(entry.history_relevance_counts?.medium), 0),
      high: entries.reduce((sum, entry) => sum + toInt(entry.history_relevance_counts?.high), 0),
    },
    history_helpfulness_counts: {
      none: entries.reduce((sum, entry) => sum + toInt(entry.history_helpfulness_counts?.none), 0),
      irrelevant: entries.reduce((sum, entry) => sum + toInt(entry.history_helpfulness_counts?.irrelevant), 0),
      low: entries.reduce((sum, entry) => sum + toInt(entry.history_helpfulness_counts?.low), 0),
      medium: entries.reduce((sum, entry) => sum + toInt(entry.history_helpfulness_counts?.medium), 0),
      high: entries.reduce((sum, entry) => sum + toInt(entry.history_helpfulness_counts?.high), 0),
    },
  };
}

function isoDatesBetween(dateFrom, dateTo) {
  const dates = [];
  for (let current = dateFrom; current <= dateTo; current = nextIsoDate(current)) {
    dates.push(current);
  }
  return dates;
}

export function buildVerifySeries(rows, groupBy, dateFrom, dateTo) {
  const rowsByDate = new Map(rows.map((row) => [String(row.bucket_date || ""), row]));
  const dailySeries = isoDatesBetween(dateFrom, dateTo).map((date) =>
    normalizeVerifySeriesEntry(rowsByDate.get(date) || {}, date, date),
  );
  if (groupBy !== "week") {
    return dailySeries;
  }
  const grouped = new Map();
  dailySeries.forEach((entry) => {
    const naturalWeekStart = weekStartIsoDate(entry.bucket_start);
    const bucketStart = maxIsoDate(naturalWeekStart, dateFrom);
    const bucketEnd = minIsoDate(addDaysIsoDate(naturalWeekStart, 6), dateTo);
    if (!grouped.has(naturalWeekStart)) {
      grouped.set(naturalWeekStart, {
        bucket_start: bucketStart,
        bucket_end: bucketEnd,
        entries: [],
      });
    }
    grouped.get(naturalWeekStart).entries.push(entry);
  });
  return Array.from(grouped.entries())
    .sort((left, right) => left[0].localeCompare(right[0]))
    .map(([, bucket]) => summarizeVerifySeriesBucket(bucket.bucket_start, bucket.bucket_end, bucket.entries));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const normalizedPath = url.pathname.replace(/\/+$/, "");

    if (
      request.method === "GET"
      && (normalizedPath === "/capabilities" || normalizedPath.endsWith("/capabilities"))
    ) {
      return jsonResponse(200, capabilitiesPayload(env));
    }

    if (request.method !== "POST") {
      return jsonResponse(405, { error: "Method not allowed." });
    }

    const payload = await readJson(request);
    if (payload === null) {
      return jsonResponse(400, { error: "Request body must be valid JSON." });
    }

    if (normalizedPath === "/stats" || normalizedPath.endsWith("/stats")) {
      const validationError = validateStatsRequest(payload);
      if (validationError) {
        return jsonResponse(400, { error: validationError });
      }

      const authToken = env.PROXY_STATS_BEARER_TOKEN;
      if (authToken) {
        const header = request.headers.get("Authorization") || "";
        if (header !== `Bearer ${authToken}`) {
          return jsonResponse(401, { error: "Missing or invalid proxy stats token." });
        }
      }

      const dateToExclusive = nextIsoDate(payload.date_to);
      const query = buildVerifyStatsQuery(payload.date_from, dateToExclusive);
      const result = await runPostHogQuery(env, query);
      if (result.error) {
        return jsonResponse(result.status, { error: result.error });
      }

      const summaryRows = queryRowsToObjects(result.payload);
      const row = summaryRows[0] || {};
      const normalized = normalizeVerifyStats(row, payload.date_from, payload.date_to);
      if (payload.group_by) {
        const seriesQuery = buildVerifySeriesQuery(payload.date_from, dateToExclusive);
        const seriesResult = await runPostHogQuery(env, seriesQuery);
        if (seriesResult.error) {
          return jsonResponse(seriesResult.status, { error: seriesResult.error });
        }
        const seriesRows = queryRowsToObjects(seriesResult.payload);
        normalized.group_by = payload.group_by;
        normalized.series = buildVerifySeries(seriesRows, payload.group_by, payload.date_from, payload.date_to);
      }
      return jsonResponse(200, normalized);
    }

    const batch = payload?.batch;
    if (!Array.isArray(batch) || batch.length === 0) {
      return jsonResponse(400, { error: "Request body must contain a non-empty batch array." });
    }
    if (batch.length > MAX_BATCH_SIZE) {
      return jsonResponse(400, { error: `Batch must contain at most ${MAX_BATCH_SIZE} events.` });
    }
    if (!batch.every(isValidEvent)) {
      return jsonResponse(400, { error: "Each batch entry must include event, distinct_id, and properties." });
    }

    const { production, dropped } = dropTestTraffic(batch);
    if (production.length === 0) {
      return jsonResponse(200, { status: 1, dropped_test_events: dropped });
    }
    return forwardBatch(env, transformBatch(production));
  },
};

export {
  CONTRACT_VERSION,
  validateStatsRequest,
  capabilitiesPayload,
  transformBatch,
  dropTestTraffic,
  buildVerifyStatsQuery,
  buildVerifySeriesQuery,
};
