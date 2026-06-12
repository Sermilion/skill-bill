import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { validateStatsRequest, capabilitiesPayload } from "./worker.js";

const VALID_DATE_RANGE = { date_from: "2026-05-01", date_to: "2026-06-01" };
const INGEST_SCHEMA_ERROR_FRAGMENT = "event_name must be the constant value";

describe("validateStatsRequest", () => {
  it("returns null for bill-feature-task (advertised workflow)", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-task", ...VALID_DATE_RANGE });
    assert.equal(err, null);
  });

  it("returns null for bill-feature-verify (advertised workflow)", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-verify", ...VALID_DATE_RANGE });
    assert.equal(err, null);
  });

  it("returns clean rejection for bill-feature-implement (unsupported workflow)", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-implement", ...VALID_DATE_RANGE });
    assert.ok(err, "should return an error string");
    assert.match(err, /workflow must be one of/);
    assert.ok(!err.includes(INGEST_SCHEMA_ERROR_FRAGMENT), "must not return the ingest-schema error");
  });

  it("returns clean rejection for bill-feature-goal (unsupported workflow)", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-goal", ...VALID_DATE_RANGE });
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
    const err = validateStatsRequest({ workflow: "bill-feature-task", date_from: "01-05-2026", date_to: "2026-06-01" });
    assert.match(err, /YYYY-MM-DD/);
  });

  it("rejects date_from after date_to", () => {
    const err = validateStatsRequest({ workflow: "bill-feature-task", date_from: "2026-06-01", date_to: "2026-05-01" });
    assert.match(err, /on or before/);
  });
});

describe("capabilitiesPayload", () => {
  const fullEnv = {
    POSTHOG_API_KEY: "key",
    POSTHOG_PERSONAL_API_KEY: "personal-key",
    POSTHOG_PROJECT_ID: "12345",
  };

  it("advertises bill-feature-task and bill-feature-verify when stats is configured", () => {
    const caps = capabilitiesPayload(fullEnv);
    assert.deepEqual(caps.supported_workflows, ["bill-feature-verify", "bill-feature-task"]);
    assert.equal(caps.supports_stats, true);
  });

  it("advertises empty supported_workflows when stats is not configured", () => {
    const caps = capabilitiesPayload({ POSTHOG_API_KEY: "key" });
    assert.deepEqual(caps.supported_workflows, []);
    assert.equal(caps.supports_stats, false);
  });
});
