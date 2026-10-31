# SmallPict Java SDK

Official Java SDK for the [SmallPict Image Optimization API](https://smallpict.app) — high-performance next-gen image transcoding (AVIF, WebP), smart compression, Edge CDN delivery, cache purging, and Spring Boot auto-configuration.

[![Maven Central](https://img.shields.io/maven-central/v/com.smallpict/smallpict-java.svg)](https://search.maven.org/artifact/com.smallpict/smallpict-java)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java 17+](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org)

---

## ⚡ Features

- **☕ 100% Native Java 11+ `HttpClient`:** Zero external third-party HTTP transport dependencies.
- **⚡ Dual Synchronous & `CompletableFuture` Asynchronous APIs:** High concurrency without blocking I/O threads.
- **🌱 Spring Boot Auto-Configuration:** Instant injection via `smallpict.api-key` in `application.yml` / `@EnableSmallPict`.
- **🛡️ Secure HMAC-SHA256 & Bearer Auth:** Tamper-proof payload verification.
- **✨ 4 Core Unified Methods:** `optimize()`, `getQuota()`, `purgeCdn()`, and `validateKey()`.
- **🔄 Resilience & Fault Tolerance:** Automatic `Idempotency-Key` UUID injection, 30s timeouts, and exponential backoff with jitter on HTTP 429/5xx.
- **🔒 Zero-Leak Privacy:** API keys and credentials are automatically redacted from error traces and exception messages.

---

## 📥 Installation

### Maven

```xml
<dependency>
    <groupId>app.smallpict</groupId>
    <artifactId>smallpict-java</artifactId>
    <version>0.0.6</version>
</dependency>
```

### Gradle

```kotlin
implementation("app.smallpict:smallpict-java:0.0.6")
```

---

## 🚀 Quick Start

### 1. Standalone Java Example

```java
import com.smallpict.SmallPictClient;
import com.smallpict.models.CompressionMode;
import com.smallpict.models.ImageFormat;
import com.smallpict.models.OptimizeOptions;
import com.smallpict.models.OptimizeResult;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        SmallPictClient client = SmallPictClient.builder()
                .apiKey(System.getenv("SMALLPICT_API_KEY"))
                .secretKey(System.getenv("SMALLPICT_SECRET_KEY")) // Optional HMAC Secret
                .build();

        OptimizeResult result = client.optimize(
                new File("hero-banner.png"),
                OptimizeOptions.builder()
                        .format(ImageFormat.AVIF)
                        .quality(80)
                        .mode(CompressionMode.LOSSY) // LOSSY (default), EXTREME, LOSSLESS or AUTO; replaces the deprecated lossless(true)
                        .maxWidth(1920)
                        .folder("blog/2026") // Optional: 1-3 segments, stored lower-cased; result.getFolder() returns it
                        .build()
        );

        System.out.println("Optimized CDN URL: " + result.getUrl());
        System.out.println("Saved: " + result.getSavingsPercentage() + "% (" + result.getBytesSaved() + " bytes)");
    }
}
```

`EXTREME` gives the smallest files on every plan. Leave `quality` out so the server uses its default for the output format; a quality you set is kept or raised to the format floor. Mode comparison: https://smallpict.app/en/docs/image-settings

```java
OptimizeResult smallest = client.optimize(
        new File("hero-banner.png"),
        OptimizeOptions.builder()
                .format(ImageFormat.AVIF)
                .mode(CompressionMode.EXTREME) // no quality(...): the server picks the AVIF default
                .build()
);
```

The server checks the mode against your plan. A mode the plan does not include fails with `PermissionDeniedException` (`mode_not_in_plan`); `getDetails().get("allowed_modes")` lists the modes you can use. `getJobStatus(jobId)` reports `getModeApplied()` and `getQualityApplied()` (`null` for older jobs).

### 2. Spring Boot 3 Integration

In `application.yml`:

```yaml
smallpict:
  api-key: ${SMALLPICT_API_KEY}
  secret-key: ${SMALLPICT_SECRET_KEY}
  fallback-mode: passthrough # throw | passthrough
```

In your Spring Controller or Service:

```java
@RestController
@RequestMapping("/api/media")
public class MediaController {
    private final SmallPictClient smallPictClient;

    public MediaController(SmallPictClient smallPictClient) {
        this.smallPictClient = smallPictClient;
    }

    @PostMapping("/upload")
    public ResponseEntity<OptimizeResult> upload(@RequestParam("file") MultipartFile file) throws IOException {
        OptimizeResult result = smallPictClient.optimize(
                file.getBytes(),
                OptimizeOptions.builder()
                        .filename(file.getOriginalFilename())
                        .format(ImageFormat.AUTO)
                        .quality(85)
                        .build()
        );
        return ResponseEntity.ok(result);
    }
}
```

---

## 📊 Account Quota & Edge CDN Purge

```java
// 1. Retrieve real-time quota
QuotaResponse quota = client.getQuota();
System.out.printf("Plan: %s, Quota Used: %.2f%%\n", quota.getPlan(), quota.getQuotaPercentage());

// 2. Invalidate Edge CDN cache
PurgeResponse purge = client.purgeCdn(List.of("https://cdn.smallpict.app/opt/hero-banner.avif"), PurgeType.URL);
System.out.println(purge.getMessage());

// 3. Purge every file uploaded to one folder
client.purgeCdn(null, PurgeType.FOLDER, "blog/2026");
```

`FOLDER` and `ALL` purges have an hourly limit per account (10 and 2). Over it, `purgeCdn` throws `RateLimitException` (`429 purge_rate_limited`) at once; the SDK does not retry it, because the limit resets within the hour, not seconds.

### Capacity and upgrade links

`getQuota().getCapacity()` returns storage, CDN, booster balances, the unpaid-account state (`getLapse()`), `getPeriodEnd()` and `getManageUrl()` for the product of your API key. A member that does not apply to your plan is `null`; `getCapacity()` itself is `null` when the server could not read it or is older. Capacity is read-only here: storage add-ons and boosters are bought in the dashboard.

```java
Capacity capacity = client.getQuota().getCapacity();
if (capacity != null && capacity.getStorage() != null) {
    System.out.printf("Storage: %d / %d bytes%n", capacity.getStorage().getUsedBytes(), capacity.getStorage().getLimitBytes());
}

try {
    client.optimize(imageBytes, OptimizeOptions.builder().build());
} catch (QuotaExceededException e) {
    System.err.println(e.getMessage() + " Add capacity: " + e.getUpgradeUrl()); // null when the error has none
}
```

Every `SmallPictException` has `getUpgradeUrl()` (`null` when absent). `storage_quota_exceeded` and `account_read_only` throw `QuotaExceededException`; `mode_not_in_plan` throws `PermissionDeniedException` with `getAllowedModes()`. The SDK never opens these links; show them to your user.

---

## 🔒 Security & Schema Compatibility (2026)

- **`mimeType` & `contentType` Dual Support**: `OptimizeOptions` provides both `getMimeType()` and `getContentType()`, and the builder accepts either `mimeType(String)` or `contentType(String)`. Requests sent via `SmallPictClient` send both `mime_type` and `content_type` fields in the payload, ensuring zero-disruption compatibility across all SmallPict API versions.

---

## 📄 License

MIT © [SmallPict Engineering](https://smallpict.app)
