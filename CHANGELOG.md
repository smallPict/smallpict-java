# Changelog

All notable changes to the `app.smallpict:smallpict-java` artifact will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Fixed
- Retry waits no longer block a `ForkJoinPool.commonPool()` thread and are capped. The backoff used `Thread.sleep` inside `runAsync`, so several concurrent 429s starved the common pool, and `Retry-After: 3600` waited an hour per attempt. The wait is now a `delayedExecutor` timer of at most `maxRetryDelay` (new builder option, default 30 s).
- Every request carries a fresh `X-Nonce`, so a retry inside the same second is no longer refused by the server as a replay (401 "Replay attack detected"); before, a transient 429 or 5xx could surface as `AuthenticationException`. `Idempotency-Key` is generated once per call and kept across retries instead of once per attempt.
- **The synchronous methods throw the typed exception.** `optimize`, `getQuota`, `purgeCdn`, `validateKey` and `getJobStatus` used `CompletableFuture.join()`, which wraps every failure in `java.util.concurrent.CompletionException`, so `catch (QuotaExceededException e)` never matched. They now rethrow the `SmallPictException` (or any `RuntimeException`) the async call failed with.

### Changed
- **`secretKey` is required.** `SmallPictConfig` throws `ValidationException` when neither `secretKey` nor `SMALLPICT_SECRET_KEY` is set. The Bearer-only mode (API key without signature) is gone: the API refuses it with 401 or 403, so a client built that way never worked. The README no longer calls the secret optional.
- Release workflow runs the test suite before `mvn deploy` (no more `-DskipTests`); a tag on a commit that fails it no longer publishes. Actions are pinned to commit SHAs. The retired OSSRH `distributionManagement` block is gone (publishing goes through the Central Portal plugin), and the compiler uses `release` 17 so the JDK 21 CI job also checks the Java 17 API.

## [0.1.0] - 2026-11-05

### Fixed
- **`optimize()` now returns an optimized image.** `POST /v1/optimize` only queues the job and returns a pre-signed `upload_url`; the SDK stopped there, so the worker failed every job with "client upload never arrived" and callers got an empty `url` with 0 % saved. `optimize()` / `optimizeAsync()` now PUT the bytes to `upload_url` (plain PUT with `Content-Type` and `Content-Length` as sent, no API headers, no retry), poll `GET /v1/optimize/status` every `pollInterval` (default 1 s) and return once the job is `completed`, with `url`, `format`, `bytesSaved`, `modeApplied`, `qualityApplied` and `folder` from the job status (`SDK_SPEC.md` section 3d).
- `optimize(File, ...)` / `optimize(Path, ...)` no longer turn every API error into `ValidationException("Failed to read file: ...")`, and `optimize(InputStream, ...)` no longer turns them into `NetworkException`; only a failed read does.

### Added
- `SmallPictClient.Builder` / `SmallPictConfig.Builder` options `pollInterval(Duration)` (default 1 s) and `waitTimeout(Duration)` (default 120 s).
- `OptimizeResult.getModeApplied()` and `getQualityApplied()`.
- Error code `job_failed` (a failed job: `SmallPictException` with the server's `error.message` and `details.job_id`) and `upload_failed` (the storage refused the PUT: `SmallPictException` with the HTTP status). A job still running after `waitTimeout` throws `TimeoutException` with the job id; it keeps running on the server.

### Changed
- **`OptimizeResult.getStatus()` is always `"completed"`** and `getUploadUrl()` is gone, together with the `uploadUrl` constructor parameters: there is no result to return before the job finished. Code that checked for `"queued"` or used `getUploadUrl()` to upload by itself can drop that.
- `compressedSize` and `savingsPercentage` are derived from the bytes sent and `bytes_saved` of the job status.
- The SDK has no URL source: pass `byte[]`, `File`, `Path` or `InputStream`. The API does not fetch URLs.
- README: "What `optimize()` does".

## [0.0.7] - 2026-10-31

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
