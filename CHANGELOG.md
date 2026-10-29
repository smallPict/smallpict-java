# Changelog

All notable changes to the `app.smallpict:smallpict-java` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `CompressionMode` enum (`LOSSY`, `EXTREME`, `LOSSLESS`) and `OptimizeOptions.Builder.mode(...)`, sent as `options.mode` on `POST /v1/optimize`. Omitted when unset, so the server default (`lossy`) applies. The server checks the mode against the plan; no client-side check.
- `JobStatusResult.getModeApplied()` (String) and `getQualityApplied()` (Integer) from the job status; `null` when the server does not report them.

### Deprecated
- `OptimizeOptions.Builder.lossless(boolean)` and `OptimizeOptions.getLossless()`. Use `mode(CompressionMode.LOSSLESS)`. `lossless(true)` without a mode is sent as `mode: "lossless"`; with a different explicit mode, `build()` throws `ValidationException`. Removal not before the next major version.

### Fixed
- `lossless(true)` used to be ignored by the server; it is now honoured (sent as `mode: "lossless"`). The `lossless` field is no longer sent on the wire.
- API error `details` were always dropped (`SmallPictException.getDetails()` returned `null`); they are now kept, so a `403 mode_not_in_plan` `PermissionDeniedException` carries `mode` and `allowed_modes`.

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
