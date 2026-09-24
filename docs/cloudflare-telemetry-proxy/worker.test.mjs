import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  validateStatsRequest,
  capabilitiesPayload,
  transformBatch,
  buildVerifyStatsQuery,
  buildVerifySeriesQuery,
  dropTestTraffic,
  normalizeVerifyStats,
  buildVerifySeries,
} from "./worker.js";
import worker from "./worker.js";
import { relayDriftError } from "./check-deployed-relay.mjs";

const VALID_DATE_RANGE = { date_from: "2026-05-01", date_to: "2026-06-01" };
const INGEST_SCHEMA_ERROR_FRAGMENT = "event_name must be the constant value";

describe("validateStatsRequest", () => {
  it("returns null for bill-feature-verify (advertised workflow)", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-verify", ...VALID_DATE_RANGE });
    assert.equal(err, null);
  });

  it("returns clean rejection for an unknown workflow", () => {
    const err = validateStatsRequest({ workflow: "bill-unknown-workflow", ...VALID_DATE_RANGE });
    assert.ok(err, "should return an error string");
    assert.match(err, /workflow must be one of/);
    assert.ok(!err.includes(INGEST_SCHEMA_ERROR_FRAGMENT), "must not return the ingest-schema error");
  });

  it("returns clean rejection for a retired feature workflow", () => {
    const err = validateStatsRequest({ workflow: "retired-feature-workflow", ...VALID_DATE_RANGE });
    assert.ok(err, "should return an error string");
    assert.match(err, /workflow must be one of/);
    assert.ok(!err.includes(INGEST_SCHEMA_ERROR_FRAGMENT), "must not return the ingest-schema error");
  });

  it("returns clean rejection for feature-task-runtime (unsupported workflow)", () => {
    const err = validateStatsRequest({ workflow: "feature-task-runtime", ...VALID_DATE_RANGE });
    assert.ok(err, "should return an error string");
    assert.match(err, /workflow must be one of/);
    assert.ok(!err.includes(INGEST_SCHEMA_ERROR_FRAGMENT), "must not return the ingest-schema error");
  });

  it("rejects invalid date_from format", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-verify", date_from: "01-05-2026", date_to: "2026-06-01" });
    assert.match(err, /YYYY-MM-DD/);
  });

  it("rejects date_from after date_to", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-verify", date_from: "2026-06-01", date_to: "2026-05-01" });
    assert.match(err, /on or before/);
  });
});

describe("capabilitiesPayload", () => {
  const fullEnv = {
    POSTHOG_API_KEY: "key",
    POSTHOG_PERSONAL_API_KEY: "personal-key",
    POSTHOG_PROJECT_ID: "12345",
  };

  it("advertises only bill-feature-verify when stats is configured", () => {
    const caps = capabilitiesPayload(fullEnv);
    assert.deepEqual(caps.supported_workflows, ["bill-feature-verify"]);
    assert.equal(caps.supports_stats, true);
  });

  it("advertises empty supported_workflows when stats is not configured", () => {
    const caps = capabilitiesPayload({ POSTHOG_API_KEY: "key" });
    assert.deepEqual(caps.supported_workflows, []);
    assert.equal(caps.supports_stats, false);
  });

  it("advertises event deduplication support so clients do not refuse to send", () => {
    assert.equal(capabilitiesPayload(fullEnv).supports_event_deduplication, true);
  });
});

describe("stats queries default to production installs", () => {
  const RANGE = ["2026-05-01", "2026-06-01"];

  [
    ["verify stats", () => buildVerifyStatsQuery(...RANGE)],
    ["verify series", () => buildVerifySeriesQuery(...RANGE)],
  ].forEach(([name, buildQuery]) => {
    it(`${name} excludes null blank and test install ids`, () => {
      const query = buildQuery();
      assert.ok(query.includes("properties.install_id IS NOT NULL"), "null install ids must be excluded");
      assert.ok(query.includes("trim(toString(properties.install_id)) != ''"), "blank install ids must be excluded");
      assert.ok(query.includes("toString(properties.install_id) != 'test-install-id'"), "test install ids must be excluded");
    });
  });
});

describe("transformBatch", () => {
  const baseEvent = (event, props = {}) => ({
    event,
    distinct_id: "user-1",
    properties: props,
  });

  it("renames skillbill_runtime_exception to $exception", () => {
    const batch = [baseEvent("skillbill_runtime_exception", { error_type: "RuntimeException", error_message: "bad" })];
    const result = transformBatch(batch);
    assert.equal(result[0].event, "$exception");
  });

  it("maps error_type to $exception_type and error_message to $exception_message", () => {
    const batch = [baseEvent("skillbill_runtime_exception", { error_type: "IllegalStateException", error_message: "state error" })];
    const result = transformBatch(batch);
    assert.equal(result[0].properties.$exception_type, "IllegalStateException");
    assert.equal(result[0].properties.$exception_message, "state error");
  });

  it("preserves original workflow_phase in transformed event properties", () => {
    const batch = [baseEvent("skillbill_runtime_exception", { workflow_phase: "my_tool", error_type: "RuntimeException", error_message: "fail" })];
    const result = transformBatch(batch);
    assert.equal(result[0].properties.workflow_phase, "my_tool");
  });

  it("does not transform non-exception events", () => {
    const batch = [
      baseEvent("skillbill_feature_verify_started", { session_id: "fvs-123" }),
      baseEvent("skillbill_runtime_exception", { error_type: "RuntimeException", error_message: "oops" }),
    ];
    const result = transformBatch(batch);
    assert.equal(result[0].event, "skillbill_feature_verify_started");
    assert.equal(result[1].event, "$exception");
  });

  // The whole replay recovery depends on the receiver seeing the producer's $insert_id. The
  // exception branch rebuilds properties, so a rewrite that forgot to spread them would silently
  // turn every retried exception into a fresh duplicate event.
  it("forwards $insert_id verbatim through both the pass-through and exception-rewrite branches", () => {
    const batch = [
      { ...baseEvent("skillbill_review_finished", { $insert_id: "pass-through-id" }) },
      {
        ...baseEvent("skillbill_runtime_exception", {
          $insert_id: "rewritten-id",
          error_type: "RuntimeException",
          error_message: "boom",
        }),
      },
    ];
    const result = transformBatch(batch);
    assert.equal(result[0].properties.$insert_id, "pass-through-id");
    assert.equal(result[1].properties.$insert_id, "rewritten-id");
    assert.equal(result[1].event, "$exception", "the second event must still be the rewritten branch");
  });

  // PostHog collapses a resend only on the top-level uuid; losing it on either branch turns every
  // retried batch back into duplicates (the 2026-08-15 storm stored one batch ~32,630 times).
  it("keeps the producer's top-level uuid on pass-through and rewritten exception events", () => {
    const result = transformBatch([
      { ...baseEvent("skillbill_goal_started"), uuid: "11111111-1111-4111-8111-111111111111" },
      { ...baseEvent("skillbill_runtime_exception", { error_type: "E" }), uuid: "22222222-2222-4222-8222-222222222222" },
    ]);
    assert.equal(result[0].uuid, "11111111-1111-4111-8111-111111111111");
    assert.equal(result[1].uuid, "22222222-2222-4222-8222-222222222222");
    assert.equal(result[1].event, "$exception");
  });

  it("builds exception frames from the redacted stack trace instead of an empty list", () => {
    const [exception] = transformBatch([
      baseEvent("skillbill_runtime_exception", {
        error_type: "IllegalStateException",
        stack_trace: "skillbill.a.A.run(A.kt:1)\nskillbill.b.B.call(B.kt:2)\nskillbill.c.C.main(C.kt:3)",
      }),
    ]);
    assert.equal(exception.properties.$exception_list[0].stacktrace.frames.length, 3);
  });

  it("disables GeoIP on every forwarded event because PostHog only sees the relay egress IP", () => {
    const result = transformBatch([baseEvent("skillbill_goal_started"), baseEvent("skillbill_runtime_exception")]);
    assert.ok(result.every((event) => event.properties.$geoip_disable === true));
  });

  it("passes retired prose event names through untouched and unaggregated", () => {
    const batch = [
      baseEvent("skillbill_feature_task_prose_started", { session_id: "ftps-1" }),
      baseEvent("skillbill_feature_implement_finished", { session_id: "fif-1" }),
    ];
    const result = transformBatch(batch);
    assert.equal(result[0].event, "skillbill_feature_task_prose_started");
    assert.equal(result[1].event, "skillbill_feature_implement_finished");
    const verifyQuery = buildVerifyStatsQuery("2026-05-01", "2026-06-01");
    assert.ok(!verifyQuery.includes("prose"), "no surviving query may aggregate retired prose events");
    assert.ok(!verifyQuery.includes("feature_implement"), "no surviving query may aggregate retired implement events");
  });
});

describe("test traffic never reaches PostHog", () => {
  const event = (distinctId, installId = distinctId) => ({
    event: "skillbill_quality_check_finished",
    distinct_id: distinctId,
    properties: installId === undefined ? {} : { install_id: installId },
  });

  it("drops the reserved test identity and blank identities and keeps real installs", () => {
    const { production, dropped } = dropTestTraffic([
      event("test-install-id"),
      event("ddd1bdbc", "  "),
      event("real-install"),
    ]);
    assert.deepEqual(production.map((e) => e.distinct_id), ["real-install"]);
    assert.equal(dropped, 2);
  });

  it("acknowledges an all-test batch without calling PostHog, so the client stops retrying", async () => {
    const originalFetch = globalThis.fetch;
    let upstreamCalls = 0;
    globalThis.fetch = async () => {
      upstreamCalls += 1;
      return new Response("{}", { status: 200 });
    };
    try {
      const response = await worker.fetch(
        new Request("https://relay.example/", {
          method: "POST",
          body: JSON.stringify({ batch: [event("test-install-id")] }),
        }),
        { POSTHOG_API_KEY: "key" },
      );
      assert.equal(response.status, 200);
      assert.equal(upstreamCalls, 0);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

describe("verify stats account for every finished run", () => {
  it("counts stale runs in their own bucket so status counts sum to finished runs", () => {
    const stats = normalizeVerifyStats(
      {
        started_runs: 80,
        finished_runs: 81,
        completion_status_completed: 54,
        completion_status_stale: 27,
        completion_status_other: 0,
      },
      "2026-06-01",
      "2026-09-24",
    );
    const counted = Object.values(stats.completion_status_counts).reduce((sum, count) => sum + count, 0);
    assert.equal(counted, stats.finished_runs);
    assert.equal(stats.finished_without_start_runs, 1);
  });

  it("averages duration over completed runs with a measured duration only", () => {
    const query = buildVerifyStatsQuery("2026-06-01", "2026-09-25");
    const durationClause = query.slice(query.indexOf("avgIf(toFloat(toString(properties.duration_seconds))"));
    assert.ok(durationClause.includes("completion_status) = 'completed'"));
    assert.ok(durationClause.includes("duration_seconds_availability"));
  });

  it("zero-fills weeks with no events instead of omitting them", () => {
    const series = buildVerifySeries(
      [
        { bucket_date: "2026-07-06", started_runs: 2, finished_runs: 2 },
        { bucket_date: "2026-07-20", started_runs: 1, finished_runs: 1 },
      ],
      "week",
      "2026-07-06",
      "2026-07-26",
    );
    assert.deepEqual(series.map((bucket) => bucket.started_runs), [2, 0, 1]);
  });
});

describe("stats failures", () => {
  it("never echoes the upstream error body to the caller", async () => {
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async () => new Response("internal query detail", { status: 500 });
    try {
      const response = await worker.fetch(
        new Request("https://relay.example/stats", {
          method: "POST",
          body: JSON.stringify({ workflow: "bill-feature-verify", date_from: "2026-06-01", date_to: "2026-06-02" }),
        }),
        { POSTHOG_PERSONAL_API_KEY: "p", POSTHOG_PROJECT_ID: "1" },
      );
      const body = await response.json();
      assert.equal(response.status, 502);
      assert.equal("details" in body, false);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });
});

describe("deployed relay drift check", () => {
  it("fails when the deployed relay reports an older contract and passes when it matches", () => {
    assert.match(relayDriftError({ contract_version: "1" }), /Redeploy/);
    assert.equal(relayDriftError(capabilitiesPayload({})), null);
  });
});
