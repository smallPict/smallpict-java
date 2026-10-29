package com.smallpict.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.smallpict.errors.ValidationException;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OptimizeOptions {
    private final ImageFormat format;
    private final Integer quality;
    @JsonProperty("max_width")
    private final Integer maxWidth;
    @JsonProperty("max_height")
    private final Integer maxHeight;
    @JsonProperty("max_dimension")
    private final Integer maxDimension;
    private final FitMode fit;
    private final CompressionMode mode;
    @JsonIgnore // deprecated: only `mode` goes on the wire
    private final Boolean lossless;
    @JsonProperty("strip_metadata")
    private final boolean stripMetadata;
    private final String filename;
    @JsonProperty("mime_type")
    private final String mimeType;
    private final String folder;
    @JsonIgnore
    private final String idempotencyKey;

    private OptimizeOptions(Builder builder) {
        this.format = builder.format;
        this.quality = builder.quality;
        this.maxWidth = builder.maxWidth;
        this.maxHeight = builder.maxHeight;
        this.maxDimension = builder.maxDimension;
        this.fit = builder.fit;
        this.lossless = builder.lossless;
        boolean losslessFlag = Boolean.TRUE.equals(builder.lossless);
        if (losslessFlag && builder.mode != null && builder.mode != CompressionMode.LOSSLESS) {
            throw new ValidationException("lossless=true contradicts mode=" + builder.mode + "; set only mode");
        }
        this.mode = builder.mode != null ? builder.mode : (losslessFlag ? CompressionMode.LOSSLESS : null);
        this.stripMetadata = builder.stripMetadata;
        this.filename = builder.filename;
        this.mimeType = builder.mimeType;
        this.folder = builder.folder;
        this.idempotencyKey = builder.idempotencyKey;
    }

    public static Builder builder() {
        return new Builder();
    }

    public ImageFormat getFormat() {
        return format;
    }

    public Integer getQuality() {
        return quality;
    }

    public Integer getMaxWidth() {
        return maxWidth;
    }

    public Integer getMaxHeight() {
        return maxHeight;
    }

    public Integer getMaxDimension() {
        return maxDimension;
    }

    public FitMode getFit() {
        return fit;
    }

    /** Compression mode sent as {@code options.mode}; {@code null} lets the server use its default ({@code lossy}). */
    public CompressionMode getMode() {
        return mode;
    }

    /** @deprecated Use {@link #getMode()}; removal not before the next major version. */
    @Deprecated
    public Boolean getLossless() {
        return lossless;
    }

    public boolean isStripMetadata() {
        return stripMetadata;
    }

    public String getFilename() {
        return filename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getContentType() {
        return mimeType;
    }

    /** Upload folder sent as {@code options.folder}; {@code null} stores the file as before. */
    public String getFolder() {
        return folder;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public static class Builder {
        private ImageFormat format = ImageFormat.AUTO;
        private Integer quality = 80;
        private Integer maxWidth;
        private Integer maxHeight;
        private Integer maxDimension;
        private FitMode fit = FitMode.COVER;
        private CompressionMode mode;
        private Boolean lossless = false;
        private boolean stripMetadata = true;
        private String filename;
        private String mimeType;
        private String folder;
        private String idempotencyKey;

        public Builder format(ImageFormat format) {
            this.format = format;
            return this;
        }

        public Builder quality(int quality) {
            this.quality = Math.max(1, Math.min(100, quality));
            return this;
        }

        public Builder maxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
            return this;
        }

        public Builder maxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
            return this;
        }

        public Builder maxDimension(int maxDimension) {
            this.maxDimension = maxDimension;
            return this;
        }

        public Builder fit(FitMode fit) {
            this.fit = fit;
            return this;
        }

        public Builder mode(CompressionMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * @deprecated Use {@link #mode(CompressionMode)} with {@link CompressionMode#LOSSLESS}; removal not before the next major version.
         * {@code true} is sent as {@code mode: "lossless"}; {@code true} with another explicit mode makes {@link #build()} throw {@link ValidationException}.
         */
        @Deprecated
        public Builder lossless(boolean lossless) {
            this.lossless = lossless;
            return this;
        }

        public Builder stripMetadata(boolean stripMetadata) {
            this.stripMetadata = stripMetadata;
            return this;
        }

        public Builder filename(String filename) {
            this.filename = filename;
            return this;
        }

        public Builder mimeType(String mimeType) {
            this.mimeType = mimeType;
            return this;
        }

        public Builder contentType(String contentType) {
            this.mimeType = contentType;
            return this;
        }

        /**
         * Folder for the stored file, 1-3 segments separated by {@code /} (e.g. {@code "blog/2026"}).
         * The server lower-cases it and rejects a malformed value with 400 {@code invalid_folder} ({@link ValidationException}).
         */
        public Builder folder(String folder) {
            this.folder = folder;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public OptimizeOptions build() {
            return new OptimizeOptions(this);
        }
    }
}
