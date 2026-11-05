package com.smallpict;

import com.smallpict.errors.ValidationException;
import com.smallpict.models.FallbackMode;
import java.time.Duration;

public class SmallPictConfig {
    private final String apiKey;
    private final String secretKey;
    private final String baseUrl;
    private final Duration timeout;
    private final int maxRetries;
    private final FallbackMode fallbackMode;
    private final Duration pollInterval;
    private final Duration waitTimeout;

    private SmallPictConfig(Builder builder) {
        String resolvedKey = builder.apiKey != null ? builder.apiKey : System.getenv("SMALLPICT_API_KEY");
        if (resolvedKey == null || resolvedKey.trim().isEmpty()) {
            throw new ValidationException("Missing required SmallPict API key. Set `apiKey` or set SMALLPICT_API_KEY environment variable.");
        }

        this.apiKey = resolvedKey;
        this.secretKey = builder.secretKey != null ? builder.secretKey : System.getenv("SMALLPICT_SECRET_KEY");
        String base = builder.baseUrl != null ? builder.baseUrl : System.getenv("SMALLPICT_BASE_URL");
        this.baseUrl = (base != null ? base : "https://api.smallpict.app").replaceAll("/+$", "");
        this.timeout = builder.timeout != null ? builder.timeout : Duration.ofSeconds(30);
        this.maxRetries = builder.maxRetries;
        this.fallbackMode = builder.fallbackMode != null ? builder.fallbackMode : FallbackMode.THROW;
        this.pollInterval = builder.pollInterval != null ? builder.pollInterval : Duration.ofSeconds(1);
        this.waitTimeout = builder.waitTimeout != null ? builder.waitTimeout : Duration.ofSeconds(120);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public FallbackMode getFallbackMode() {
        return fallbackMode;
    }

    /** How often optimize() polls the job status after the upload (default 1 s). */
    public Duration getPollInterval() {
        return pollInterval;
    }

    /** How long optimize() waits for the job before throwing TimeoutException (default 120 s). */
    public Duration getWaitTimeout() {
        return waitTimeout;
    }

    public static class Builder {
        private String apiKey;
        private String secretKey;
        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxRetries = 3;
        private FallbackMode fallbackMode = FallbackMode.THROW;
        private Duration pollInterval = Duration.ofSeconds(1);
        private Duration waitTimeout = Duration.ofSeconds(120);

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder fallbackMode(FallbackMode fallbackMode) {
            this.fallbackMode = fallbackMode;
            return this;
        }

        public Builder pollInterval(Duration pollInterval) {
            this.pollInterval = pollInterval;
            return this;
        }

        public Builder waitTimeout(Duration waitTimeout) {
            this.waitTimeout = waitTimeout;
            return this;
        }

        public SmallPictConfig build() {
            return new SmallPictConfig(this);
        }
    }
}
