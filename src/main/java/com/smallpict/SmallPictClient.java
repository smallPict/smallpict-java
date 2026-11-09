package com.smallpict;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smallpict.errors.AuthenticationException;
import com.smallpict.errors.NetworkException;
import com.smallpict.errors.NotFoundException;
import com.smallpict.errors.PermissionDeniedException;
import com.smallpict.errors.QuotaExceededException;
import com.smallpict.errors.RateLimitException;
import com.smallpict.errors.ServerException;
import com.smallpict.errors.SmallPictException;
import com.smallpict.errors.TimeoutException;
import com.smallpict.errors.ValidationException;
import com.smallpict.models.FallbackMode;
import com.smallpict.models.JobStatusResult;
import com.smallpict.models.OptimizeOptions;
import com.smallpict.models.OptimizeResult;
import com.smallpict.models.PurgeResponse;
import com.smallpict.models.PurgeType;
import com.smallpict.models.QuotaResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SmallPictClient {
    private final SmallPictConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public SmallPictClient(SmallPictConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getTimeout())
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SmallPictClient(String apiKey) {
        this(SmallPictConfig.builder().apiKey(apiKey).build());
    }

    public static SmallPictClient.Builder builder() {
        return new Builder();
    }

    // --- Synchronous 4 Core Methods ---

    /** join() wraps failures in CompletionException; callers of the sync API get the typed exception. */
    private static <T> T sync(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            throw e;
        }
    }

    /**
     * Queues the job, uploads the bytes and waits for the finished result.
     * A retry after a 5xx may create a second job until the server honours {@code Idempotency-Key}.
     */
    public OptimizeResult optimize(byte[] data, OptimizeOptions options) {
        return sync(optimizeAsync(data, options));
    }

    public OptimizeResult optimize(File file, OptimizeOptions options) {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file.toPath());
        } catch (Exception e) {
            throw new ValidationException("Failed to read file: " + e.getMessage());
        }
        OptimizeOptions opts = options != null ? options : OptimizeOptions.builder().filename(file.getName()).build();
        return optimize(bytes, opts);
    }

    public OptimizeResult optimize(Path path, OptimizeOptions options) {
        return optimize(path.toFile(), options);
    }

    public OptimizeResult optimize(InputStream stream, OptimizeOptions options) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = stream.read(chunk, 0, chunk.length)) != -1) {
                buffer.write(chunk, 0, read);
            }
        } catch (Exception e) {
            throw new NetworkException("Failed to read image stream: " + e.getMessage(), e);
        }
        return optimize(buffer.toByteArray(), options);
    }

    public QuotaResponse getQuota() {
        return sync(getQuotaAsync());
    }

    public PurgeResponse purgeCdn(List<String> urls, PurgeType purgeType) {
        return sync(purgeCdnAsync(urls, purgeType));
    }

    /** Purge with a folder; {@code folder} is required for {@link PurgeType#FOLDER} and sent only with it. */
    public PurgeResponse purgeCdn(List<String> urls, PurgeType purgeType, String folder) {
        return sync(purgeCdnAsync(urls, purgeType, folder));
    }

    public boolean validateKey() {
        return sync(validateKeyAsync());
    }

    public JobStatusResult getJobStatus(String jobId) {
        return sync(getJobStatusAsync(jobId));
    }

    // --- Asynchronous CompletableFuture Methods ---

    public CompletableFuture<OptimizeResult> optimizeAsync(byte[] data, OptimizeOptions options) {
        OptimizeOptions opts = options != null ? options : OptimizeOptions.builder().build();
        if (opts.getFolder() != null) {
            validateFolder(opts.getFolder());
        }
        String filename = opts.getFilename() != null ? opts.getFilename() : "image.jpg";
        String mimeType = opts.getMimeType() != null ? opts.getMimeType() : "image/jpeg";

        Map<String, Object> payload = new HashMap<>();
        payload.put("filename", filename);
        payload.put("mime_type", mimeType);
        payload.put("content_type", mimeType);
        payload.put("filesize", data.length);
        payload.put("options", opts);

        return executeRequestAsync("POST", "/v1/optimize", payload, opts.getIdempotencyKey())
                .handle((queued, throwable) -> {
                    if (throwable == null) {
                        String jobId = queued.path("job_id").asText();
                        // The worker waits 30 s from the request for the object: upload now, poll after.
                        return uploadAsync(queued.path("upload_url").asText(null), data, mimeType)
                                .thenCompose(v -> waitForJobAsync(jobId, data.length, System.nanoTime() + config.getWaitTimeout().toNanos()));
                    }
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof QuotaExceededException && config.getFallbackMode() == FallbackMode.PASSTHROUGH) {
                        String formatStr = mimeType.replace("image/", "");
                        return CompletableFuture.completedFuture(new OptimizeResult(
                                "fallback-passthrough",
                                "completed",
                                "",
                                formatStr,
                                data.length,
                                data.length,
                                0L,
                                0.0,
                                data
                        ));
                    }
                    return CompletableFuture.<OptimizeResult>failedFuture(cause instanceof SmallPictException
                            ? cause : new SmallPictException(cause.getMessage()));
                })
                .thenCompose(f -> f);
    }

    /** Plain PUT to the pre-signed URL: only Content-Type and Content-Length, which S3 checks against the signature. No retry. */
    private CompletableFuture<Void> uploadAsync(String uploadUrl, byte[] data, String mimeType) {
        if (uploadUrl == null || uploadUrl.isEmpty()) {
            return CompletableFuture.failedFuture(new SmallPictException("The optimize response has no upload_url."));
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .timeout(config.getTimeout())
                .header("Content-Type", mimeType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data)) // sets Content-Length = data.length
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                        throw new NetworkException("Upload to storage failed: " + cause.getMessage(), cause);
                    }
                    int status = response.statusCode();
                    if (status < 200 || status >= 300) {
                        throw new SmallPictException("Upload to storage failed with HTTP " + status + ": " + response.body(),
                                "upload_failed", status, null, null, null);
                    }
                    return null;
                });
    }

    private CompletableFuture<OptimizeResult> waitForJobAsync(String jobId, long originalSize, long deadline) {
        long waitMs = Math.min(config.getPollInterval().toMillis(),
                Math.max(0L, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())));
        return CompletableFuture.runAsync(() -> {}, CompletableFuture.delayedExecutor(waitMs, TimeUnit.MILLISECONDS))
                .thenCompose(v -> getJobStatusAsync(jobId))
                .thenCompose(status -> {
                    if ("completed".equals(status.getStatus())) {
                        long bytesSaved = status.getBytesSaved() != null ? status.getBytesSaved() : 0L;
                        return CompletableFuture.completedFuture(new OptimizeResult(
                                status.getJobId(), "completed", status.getUrl(), status.getFormat(),
                                originalSize, originalSize - bytesSaved, bytesSaved, null,
                                status.getModeApplied(), status.getQualityApplied(), status.getFolder()));
                    }
                    if ("failed".equals(status.getStatus())) {
                        Object message = status.getError() != null ? status.getError().get("message") : null;
                        throw new SmallPictException(
                                message instanceof String && !((String) message).isEmpty()
                                        ? (String) message : "The optimization job failed.",
                                "job_failed", null, null, Map.of("job_id", jobId), null);
                    }
                    if (System.nanoTime() - deadline >= 0) {
                        throw new TimeoutException("Job " + jobId + " did not finish within "
                                + config.getWaitTimeout().toMillis() + "ms; poll getJobStatus(\"" + jobId + "\") later.");
                    }
                    return waitForJobAsync(jobId, originalSize, deadline);
                });
    }

    public CompletableFuture<QuotaResponse> getQuotaAsync() {
        return executeRequestAsync("GET", "/v1/quota", null, null)
                .thenApply(json -> {
                    try {
                        return objectMapper.treeToValue(json, QuotaResponse.class);
                    } catch (Exception e) {
                        throw new SmallPictException("Failed to parse QuotaResponse: " + e.getMessage());
                    }
                });
    }

    public CompletableFuture<PurgeResponse> purgeCdnAsync(List<String> urls, PurgeType purgeType) {
        return purgeCdnAsync(urls, purgeType, null);
    }

    /** Purge with a folder; {@code folder} is required for {@link PurgeType#FOLDER} and sent only with it. */
    public CompletableFuture<PurgeResponse> purgeCdnAsync(List<String> urls, PurgeType purgeType, String folder) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("purge_type", purgeType != null ? purgeType.getValue() : "url");
        payload.put("urls", urls != null ? urls : Collections.emptyList());
        if (purgeType == PurgeType.FOLDER) {
            if (folder == null || folder.trim().isEmpty()) {
                throw new ValidationException("folder is required for purge type \"folder\"");
            }
            validateFolder(folder);
            payload.put("folder", folder);
        }

        return executeRequestAsync("POST", "/v1/purge", payload, null)
                .thenApply(json -> {
                    try {
                        return objectMapper.treeToValue(json, PurgeResponse.class);
                    } catch (Exception e) {
                        throw new SmallPictException("Failed to parse PurgeResponse: " + e.getMessage());
                    }
                });
    }

    public CompletableFuture<Boolean> validateKeyAsync() {
        return getQuotaAsync()
                .thenApply(quota -> true)
                .exceptionally(t -> false);
    }

    public CompletableFuture<JobStatusResult> getJobStatusAsync(String jobId) {
        if (jobId == null || jobId.trim().isEmpty()) {
            CompletableFuture<JobStatusResult> failed = new CompletableFuture<>();
            failed.completeExceptionally(new ValidationException("jobId parameter is required"));
            return failed;
        }

        String path = "/v1/optimize/status?job_id=" + URLEncoder.encode(jobId, StandardCharsets.UTF_8);
        return executeRequestAsync("GET", path, null, null)
                .thenApply(json -> {
                    try {
                        return objectMapper.treeToValue(json, JobStatusResult.class);
                    } catch (Exception e) {
                        throw new SmallPictException("Failed to parse JobStatusResult: " + e.getMessage());
                    }
                });
    }

    // shortcut: catches the obvious mistakes only; the server's 400 invalid_folder is the authority on the full format.
    private static void validateFolder(String folder) {
        String[] segments = folder.split("/", -1);
        if (segments.length > 3) {
            throw new ValidationException("folder has more than 3 segments: " + folder);
        }
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals("..")) {
                throw new ValidationException("folder has an empty or \"..\" segment: " + folder);
            }
        }
    }

    // --- Internal HTTP Pipeline ---

    private CompletableFuture<JsonNode> executeRequestAsync(
            String method,
            String path,
            Object bodyObj,
            String idempotencyKey) {
        String cleanPath = path.startsWith("/") ? path : "/" + path;
        if (!cleanPath.startsWith("/v1/") && !cleanPath.startsWith("/v2/")) {
            cleanPath = "/v1" + cleanPath;
        }

        String endpointUrl = config.getBaseUrl() + cleanPath;
        byte[] bodyBytes = null;
        String bodyHash = Crypto.EMPTY_SHA256;

        if (bodyObj != null) {
            try {
                bodyBytes = objectMapper.writeValueAsBytes(bodyObj);
                bodyHash = Crypto.sha256Hex(bodyBytes);
            } catch (Exception e) {
                CompletableFuture<JsonNode> failed = new CompletableFuture<>();
                failed.completeExceptionally(new ValidationException("Failed to serialize request body: " + e.getMessage()));
                return failed;
            }
        }

        String idem = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();
        return executeWithRetryAsync(method, endpointUrl, cleanPath, bodyBytes, bodyHash, idem, 1);
    }

    private CompletableFuture<JsonNode> executeWithRetryAsync(
            String method,
            String endpointUrl,
            String path,
            byte[] bodyBytes,
            String bodyHash,
            String idempotencyKey,
            int attempt) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .timeout(config.getTimeout())
                .header("Accept", "application/json")
                .header("X-API-Key", config.getApiKey());

        if (bodyBytes != null) {
            builder.header("Content-Type", "application/json");
            builder.method(method, HttpRequest.BodyPublishers.ofByteArray(bodyBytes));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }

        String stringToSign = Crypto.buildStringToSign(method, path, timestamp, bodyHash);
        builder.header("X-Timestamp", timestamp);
        builder.header("X-Signature", Crypto.hmacSha256Hex(config.getSecretKey(), stringToSign));

        // The server rejects a repeated signature as a replay unless the nonce differs.
        builder.header("X-Nonce", UUID.randomUUID().toString());
        if ("POST".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            builder.header("Idempotency-Key", idempotencyKey);
        }

        return httpClient.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
                .thenCompose(response -> {
                    int statusCode = response.statusCode();
                    String requestId = response.headers().firstValue("x-request-id").orElse(null);
                    Long retryAfter = response.headers().firstValue("retry-after")
                            .flatMap(s -> {
                                try { return java.util.Optional.of(Long.parseLong(s)); }
                                catch (NumberFormatException e) { return java.util.Optional.empty(); }
                            }).orElse(null);

                    JsonNode jsonNode = null;
                    if (response.body() != null && response.body().length > 0) {
                        try {
                            jsonNode = objectMapper.readTree(response.body());
                        } catch (Exception ignored) {}
                    }
                    // The hourly purge limit does not reset in seconds, so retrying it only burns attempts.
                    boolean purgeRateLimited = statusCode == 429 && jsonNode != null
                            && "purge_rate_limited".equals(jsonNode.path("error").path("code").asText());

                    if ((statusCode == 429 || (statusCode >= 500 && statusCode <= 504)) && !purgeRateLimited
                            && attempt <= config.getMaxRetries()) {
                        final long delayMs = (retryAfter != null && retryAfter > 0)
                                ? retryAfter * 1000L
                                : 250L * (1L << (attempt - 1));
                        final long jitter = ThreadLocalRandom.current().nextLong(0, 100);
                        // A timer, not Thread.sleep on the common pool: many 429s at once would starve it.
                        final long waitMs = Math.min(delayMs + jitter, config.getMaxRetryDelay().toMillis());

                        return CompletableFuture.runAsync(() -> {
                        }, CompletableFuture.delayedExecutor(waitMs, TimeUnit.MILLISECONDS)).thenCompose(v -> executeWithRetryAsync(method, endpointUrl, path, bodyBytes, bodyHash, idempotencyKey, attempt + 1));
                    }

                    if (statusCode < 200 || statusCode >= 300) {
                        handleError(statusCode, jsonNode, requestId, retryAfter);
                    }
                    if (jsonNode == null) {
                        // A proxy or WAF page with a 2xx: callers used to get null (or an NPE in optimize).
                        throw new SmallPictException("Empty or non-JSON response (HTTP " + statusCode + ")",
                                "INVALID_RESPONSE", statusCode, requestId, null, null);
                    }

                    return CompletableFuture.completedFuture(jsonNode);
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    if (cause instanceof SmallPictException) {
                        throw (SmallPictException) cause;
                    }
                    if (cause instanceof java.net.http.HttpTimeoutException) {
                        throw new TimeoutException("HTTP request timed out: " + cause.getMessage(), cause);
                    }
                    throw new NetworkException("HTTP communication error: " + cause.getMessage(), cause);
                });
    }

    private void handleError(int status, JsonNode body, String requestId, Long retryAfter) {
        String message = "API request failed with HTTP " + status;
        Map<String, Object> details = null;
        String code = null;

        if (body != null) {
            if (body.has("error")) {
                JsonNode errNode = body.get("error");
                if (errNode.isObject() && errNode.path("code").isTextual()) {
                    code = errNode.get("code").asText();
                }
                if (errNode.isObject() && errNode.has("message")) {
                    message = errNode.get("message").asText();
                } else if (errNode.isTextual()) {
                    message = errNode.asText();
                }
                if (errNode.isObject() && errNode.path("details").isObject()) {
                    details = objectMapper.convertValue(errNode.get("details"), new TypeReference<Map<String, Object>>() {});
                }
            } else if (body.has("message")) {
                message = body.get("message").asText();
            }
        }

        switch (status) {
            case 400: throw new ValidationException(message, code, requestId, details);
            case 401: throw new AuthenticationException(message, code, requestId, details);
            case 402: throw new QuotaExceededException(message, code, requestId, details);
            case 403: throw new PermissionDeniedException(message, code, requestId, details);
            case 404: throw new NotFoundException(message, code, requestId, details);
            case 429: throw new RateLimitException(message, code, retryAfter, requestId, details);
            default:
                if (status >= 500) {
                    throw new ServerException(message, code, status, requestId, details);
                }
                throw new SmallPictException(message, code != null ? code : "INTERNAL_ERROR", status, requestId, details, null);
        }
    }

    public static class Builder {
        private final SmallPictConfig.Builder configBuilder = SmallPictConfig.builder();

        public Builder apiKey(String apiKey) {
            configBuilder.apiKey(apiKey);
            return this;
        }

        public Builder secretKey(String secretKey) {
            configBuilder.secretKey(secretKey);
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            configBuilder.baseUrl(baseUrl);
            return this;
        }

        public Builder timeout(Duration timeout) {
            configBuilder.timeout(timeout);
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            configBuilder.maxRetries(maxRetries);
            return this;
        }

        public Builder fallbackMode(FallbackMode fallbackMode) {
            configBuilder.fallbackMode(fallbackMode);
            return this;
        }

        public Builder pollInterval(Duration pollInterval) {
            configBuilder.pollInterval(pollInterval);
            return this;
        }

        public Builder waitTimeout(Duration waitTimeout) {
            configBuilder.waitTimeout(waitTimeout);
            return this;
        }

        public Builder maxRetryDelay(Duration maxRetryDelay) {
            configBuilder.maxRetryDelay(maxRetryDelay);
            return this;
        }

        public SmallPictClient build() {
            return new SmallPictClient(configBuilder.build());
        }
    }
}
