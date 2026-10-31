package com.smallpict.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Read-only capacity of the key's product from {@code GET /v1/quota} ({@code QuotaCapacity}).
 * A member that does not apply to the product or plan is {@code null}. Dates are ISO-8601 strings
 * as sent by the server. {@link #getManageUrl()} is shown to the user; the SDK never opens it.
 */
public class Capacity {
    private final String periodEnd;
    private final Storage storage;
    private final Cdn cdn;
    private final Booster booster;
    private final Lapse lapse;
    private final String manageUrl;

    @JsonCreator
    public Capacity(
            @JsonProperty("period_end") String periodEnd,
            @JsonProperty("storage") Storage storage,
            @JsonProperty("cdn") Cdn cdn,
            @JsonProperty("booster") Booster booster,
            @JsonProperty("lapse") Lapse lapse,
            @JsonProperty("manage_url") String manageUrl) {
        this.periodEnd = periodEnd;
        this.storage = storage;
        this.cdn = cdn;
        this.booster = booster;
        this.lapse = lapse;
        this.manageUrl = manageUrl;
    }

    public String getPeriodEnd() {
        return periodEnd;
    }

    public Storage getStorage() {
        return storage;
    }

    public Cdn getCdn() {
        return cdn;
    }

    public Booster getBooster() {
        return booster;
    }

    public Lapse getLapse() {
        return lapse;
    }

    public String getManageUrl() {
        return manageUrl;
    }

    public static class Storage {
        private final long limitBytes;
        private final long usedBytes;
        private final long baseBytes;
        private final long addonBlocks;

        @JsonCreator
        public Storage(
                @JsonProperty("limit_bytes") long limitBytes,
                @JsonProperty("used_bytes") long usedBytes,
                @JsonProperty("base_bytes") long baseBytes,
                @JsonProperty("addon_blocks") long addonBlocks) {
            this.limitBytes = limitBytes;
            this.usedBytes = usedBytes;
            this.baseBytes = baseBytes;
            this.addonBlocks = addonBlocks;
        }

        public long getLimitBytes() {
            return limitBytes;
        }

        public long getUsedBytes() {
            return usedBytes;
        }

        public long getBaseBytes() {
            return baseBytes;
        }

        public long getAddonBlocks() {
            return addonBlocks;
        }
    }

    public static class Cdn {
        private final long limitBytes;
        private final long usedBytes;
        private final String status;

        @JsonCreator
        public Cdn(
                @JsonProperty("limit_bytes") long limitBytes,
                @JsonProperty("used_bytes") long usedBytes,
                @JsonProperty("status") String status) {
            this.limitBytes = limitBytes;
            this.usedBytes = usedBytes;
            this.status = status;
        }

        public long getLimitBytes() {
            return limitBytes;
        }

        public long getUsedBytes() {
            return usedBytes;
        }

        /** {@code ok}, {@code warn_80}, {@code exceeded_grace} or {@code hard_block}. */
        public String getStatus() {
            return status;
        }
    }

    public static class Booster {
        private final boolean active;
        private final Long wpQuotaBytes;
        private final Long apiTransforms;
        private final Long cdnBytes;
        private final NextExpiry nextExpiry;

        @JsonCreator
        public Booster(
                @JsonProperty("active") boolean active,
                @JsonProperty("wp_quota_bytes") Long wpQuotaBytes,
                @JsonProperty("api_transforms") Long apiTransforms,
                @JsonProperty("cdn_bytes") Long cdnBytes,
                @JsonProperty("next_expiry") NextExpiry nextExpiry) {
            this.active = active;
            this.wpQuotaBytes = wpQuotaBytes;
            this.apiTransforms = apiTransforms;
            this.cdnBytes = cdnBytes;
            this.nextExpiry = nextExpiry;
        }

        public boolean isActive() {
            return active;
        }

        public Long getWpQuotaBytes() {
            return wpQuotaBytes;
        }

        public Long getApiTransforms() {
            return apiTransforms;
        }

        public Long getCdnBytes() {
            return cdnBytes;
        }

        public NextExpiry getNextExpiry() {
            return nextExpiry;
        }
    }

    public static class NextExpiry {
        private final String kind;
        private final long units;
        private final String expiresAt;

        @JsonCreator
        public NextExpiry(
                @JsonProperty("kind") String kind,
                @JsonProperty("units") long units,
                @JsonProperty("expires_at") String expiresAt) {
            this.kind = kind;
            this.units = units;
            this.expiresAt = expiresAt;
        }

        /** {@code wp_quota_bytes}, {@code api_transforms} or {@code cdn_bytes}. */
        public String getKind() {
            return kind;
        }

        public long getUnits() {
            return units;
        }

        public String getExpiresAt() {
            return expiresAt;
        }
    }

    public static class Lapse {
        private final String stage;
        private final String deleteAfter;

        @JsonCreator
        public Lapse(
                @JsonProperty("stage") String stage,
                @JsonProperty("delete_after") String deleteAfter) {
            this.stage = stage;
            this.deleteAfter = deleteAfter;
        }

        /** {@code grace}, {@code blocked} or {@code deleting}. */
        public String getStage() {
            return stage;
        }

        public String getDeleteAfter() {
            return deleteAfter;
        }
    }
}
