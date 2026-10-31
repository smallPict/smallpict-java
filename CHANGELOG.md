# Changelog

All notable changes to the `app.smallpict:smallpict-java` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `CompressionMode.AUTO` (`"auto"`): the server picks the lowest quality per image that keeps the visual score (SSIMULACRA 2) at or above 70, and ignores `quality` (CMP-07). Plans: WP Pro and Business, Velocity, Momentum and sandbox (5 auto jobs per minute per sandbox key); where it cannot search (PNG or GIF output, animation, time limit) the job is lossy and `modeApplied` says so. A server without auto enabled answers `400 invalid_mode`.
- `CompressionMode` enum (`LOSSY`, `EXTREME`, `LOSSLESS`) and `OptimizeOptions.Builder.mode(...)`, sent as `options.mode` on `POST /v1/optimize`. Omitted when unset, so the server default (`lossy`) applies. The server checks the mode against the plan; no client-side check.
- `JobStatusResult.getModeApplied()` (String) and `getQualityApplied()` (Integer) from the job status; `null` when the server does not report them.
- **Upload folder.** `OptimizeOptions.Builder.folder(String)`, sent as `options.folder` on `POST /v1/optimize` and omitted when unset. `OptimizeResult.getFolder()` and `JobStatusResult.getFolder()` return the folder as the server stored it (lower case); `null` without one. Empty segments (`a//b`, leading or trailing `/`), `..` segments and more than 3 segments throw `ValidationException` before any request; the server checks the full format and its 400 `invalid_folder` also surfaces as `ValidationException` with the server's message.
- **Folder purge.** `PurgeType.FOLDER` and `purgeCdn(urls, purgeType, folder)` / `purgeCdnAsync(urls, purgeType, folder)`. `folder` is required for `FOLDER` (`ValidationException` before any request) and sent only with it. `URL` and `ALL` purges are unchanged.
- **Capacity in the quota.** `QuotaResponse.getCapacity()` returns the read-only `Capacity` of the key's product from `GET /v1/quota`: `getPeriodEnd()`, `getStorage()` (`limitBytes`, `usedBytes`, `baseBytes`, `addonBlocks`), `getCdn()` (`limitBytes`, `usedBytes`, `status`), `getBooster()` (`isActive()`, `wpQuotaBytes`, `apiTransforms`, `cdnBytes`, `nextExpiry`), `getLapse()` (`stage`, `deleteAfter`) and `getManageUrl()`. Members that do not apply are `null`; `getCapacity()` is `null` when the server could not read it and on older servers, whose responses still parse. The existing `QuotaResponse` constructor and fields are unchanged.
- **Upgrade links on errors.** `SmallPictException.getUpgradeUrl()` returns `details.upgrade_url` when it is a string, otherwise `null`: the dashboard page where the missing capacity is added (`storage_quota_exceeded`, `account_read_only` and `QUOTA_EXCEEDED` throw `QuotaExceededException`). The SDK never opens or fetches it.
- `PermissionDeniedException.getAllowedModes()` for `403 mode_not_in_plan` (from `details.allowed_modes`, otherwise `null`).

### Changed
- A non-5xx status without its own exception type (for example `409 booster_period_limit_exceeded`) still throws the base `SmallPictException`, but `getErrorCode()` now returns the server's `error.code` instead of always `INTERNAL_ERROR`. `INTERNAL_ERROR` remains the fallback when the body has no code.
- `OptimizeOptions` no longer defaults `quality` to 80: `options.quality` is sent only when you call `quality(...)`, so the server picks the default per mode (`EXTREME` now gets its per-codec default instead of 80; `LOSSY` still gets 80). `getQuality()` returns `null` when unset (possible breaking change for code that unboxes it to `int`). README has an `EXTREME` example without quality.
- A `429` with error code `purge_rate_limited` (hourly limit of `FOLDER` / `ALL` purges) now throws `RateLimitException` without automatic retries; the limit resets within the hour, so retrying only delayed the error. Other `429` responses are retried as before.
- Maven coordinates moved from `io.github.tuxnoob:smallpict-java` to `app.smallpict:smallpict-java`. Update your dependency; the old coordinates receive no new releases.

### Deprecated
- `OptimizeOptions.Builder.lossless(boolean)` and `OptimizeOptions.getLossless()`. Use `mode(CompressionMode.LOSSLESS)`. `lossless(true)` without a mode is sent as `mode: "lossless"`; with a different explicit mode, `build()` throws `ValidationException`. Removal not before the next major version.

### Fixed
- `mode(CompressionMode.EXTREME)` without `quality(...)` produced files the size of lossy quality 80, because the SDK always sent `quality: 80`.
- `lossless(true)` used to be ignored by the server; it is now honoured (sent as `mode: "lossless"`). The `lossless` field is no longer sent on the wire.
- API error `details` were always dropped (`SmallPictException.getDetails()` returned `null`); they are now kept, so a `403 mode_not_in_plan` `PermissionDeniedException` carries `mode` and `allowed_modes`.

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
