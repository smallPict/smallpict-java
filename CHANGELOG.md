# Changelog

All notable changes to the `app.smallpict:smallpict-java` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed
- Maven coordinates moved from `io.github.tuxnoob:smallpict-java` to `app.smallpict:smallpict-java`. Update your dependency; the old coordinates receive no new releases.

## [1.0.0] - 2026-08-22

### Added
- Official Java SDK implementation for SmallPict OpenAPI 3.1.0 API targeting Java 17+ baseline.
- Zero third-party HTTP dependencies using standard library `java.net.http.HttpClient`.
- Dual synchronous and `CompletableFuture` asynchronous non-blocking APIs.
- Spring Boot 3 auto-configuration (`SmallPictAutoConfiguration`, `@EnableSmallPict`, `SmallPictProperties`).
- 4 unified core client methods: `optimize`, `getQuota`, `purgeCdn`, and `validateKey`.
- Helper `getJobStatus` for polling asynchronous image conversion tasks.
- Custom exception hierarchy with automatic regex credential sanitization.
- Resilient HTTP transport with 30s timeouts, exponential backoff, jitter, and automatic `Idempotency-Key` UUID injection.
- Optional `FallbackMode.PASSTHROUGH` for uninterrupted availability on quota limit exhaustion.
