---
name: bill-php-code-review-reliability
description: Use when reviewing PHP reliability risks including timeouts, retries, queues, schedulers, caches, downstream failures, logging, metrics, and tracing.
---

# Backend Reliability Review Specialist

Review only backend/service reliability issues that can cause outages, stuck work, runaway retries, or production incidents.

## Focus

- Timeout, retry, and backoff correctness
- Background jobs, consumers, schedulers, and replay safety
- Blocking or heavy work on latency-sensitive request or worker execution paths
- Cache, queue, and downstream dependency failure behavior
- Logging, metrics, and tracing gaps that hide real failures

## Ignore

- Pure style comments
- Tiny observability niceties without incident impact

## Project-Specific Rules

- Retries must be bounded and reserved for transient failures; include backoff and jitter where stampedes are possible
- Circuit breakers, bulkheads, and rate-limiting configuration must have sensible thresholds and avoid infinite blocks, silent drops, or retry storms
- External calls should have explicit timeout behavior and a clear cancellation story
- Message consumers and scheduled jobs must be safe under duplicate delivery and partial failure
- Replay, rebuild, and republish flows must be bounded, observable, and safe to run more than once
- Acknowledge or commit work only after durable success, not before
- Avoid blocking or heavy work on latency-sensitive request or worker execution paths
- Queue, event, and notification dispatch that must happen after commit should respect the project's after-commit or outbox strategy and must not fire early
- Cache fill, refresh, and invalidation logic must not create obvious thundering-herd or stale-data incidents
- Degradation and fallback behavior should fail gracefully and make partial availability explicit where clients or operators need to know
- Logging, metrics, and tracing should include enough contextual and correlation identifiers to debug failures without leaking secrets or sensitive data
- Long-running jobs and consumers should emit enough progress/error context to distinguish poison messages, transient failures, and permanent contract/data issues
- Rate limiting, backpressure, and batch sizing should protect downstream systems and avoid retry amplification under load
