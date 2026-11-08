package com.smallpict;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smallpict.errors.PermissionDeniedException;
import com.smallpict.errors.QuotaExceededException;
import com.smallpict.errors.SmallPictException;
import com.smallpict.models.Capacity;
import com.smallpict.models.QuotaResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** SDK-02: capacity in GET /v1/quota and upgrade links on errors. Fixtures copied from smallpict-sdk-shared/capacity.json. */
class CapacityTest {
    private static final String BILLING = "https://smallpict.app/dashboard/billing";

    private static final String WORDPRESS_WITH_CAPACITY = "{\"plan\":\"pro\",\"bytes_used\":1048576,\"capacity\":{"
            + "\"period_end\":\"2026-11-30T00:00:00Z\","
            + "\"storage\":{\"limit_bytes\":75161927680,\"used_bytes\":7516192768,\"base_bytes\":53687091200,\"addon_blocks\":2},"
            + "\"cdn\":{\"limit_bytes\":53687091200,\"used_bytes\":44023414784,\"status\":\"warn_80\"},"
            + "\"booster\":{\"active\":true,\"wp_quota_bytes\":2147483648,\"api_transforms\":null,\"cdn_bytes\":107374182400,"
            + "\"next_expiry\":{\"kind\":\"cdn_bytes\",\"units\":42949672960,\"expires_at\":\"2027-01-05T00:00:00Z\"}},"
            + "\"lapse\":null,\"manage_url\":\"https://smallpict.app/dashboard/billing\"}}";
    private static final String API_BLOCKED = "{\"plan\":\"velocity\",\"bytes_used\":0,\"capacity\":{"
            + "\"period_end\":null,"
            + "\"storage\":{\"limit_bytes\":107374182400,\"used_bytes\":1073741824,\"base_bytes\":107374182400,\"addon_blocks\":0},"
            + "\"cdn\":null,"
            + "\"booster\":{\"active\":false,\"wp_quota_bytes\":null,\"api_transforms\":5000,\"cdn_bytes\":null,\"next_expiry\":null},"
            + "\"lapse\":{\"stage\":\"blocked\",\"delete_after\":\"2027-01-28T00:00:00Z\"},"
            + "\"manage_url\":\"https://smallpict.app/dashboard/billing\"}}";
    private static final String SANDBOX = "{\"plan\":\"sandbox\",\"bytes_used\":0,\"capacity\":{"
            + "\"period_end\":null,\"storage\":null,"
            + "\"cdn\":{\"limit_bytes\":1073741824,\"used_bytes\":0,\"status\":\"ok\"},"
            + "\"booster\":{\"active\":false,\"wp_quota_bytes\":null,\"api_transforms\":0,\"cdn_bytes\":0,\"next_expiry\":null},"
            + "\"lapse\":null,\"manage_url\":\"https://smallpict.app/dashboard/billing\"}}";
    private static final String CAPACITY_UNREADABLE = "{\"plan\":\"pro\",\"bytes_used\":0,\"capacity\":null}";
    private static final String OLDER_SERVER_WITHOUT_CAPACITY = "{\"plan\":\"pro\",\"bytes_used\":0}";

    private final ObjectMapper mapper = new ObjectMapper();
    private MockWebServer server;
    private SmallPictClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = SmallPictClient.builder()
                .apiKey("sp_sdk_test")
                .secretKey("sec_test")
                .baseUrl(server.url("/").toString())
                .maxRetries(0)
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private QuotaResponse quota(String body) {
        server.enqueue(new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body));
        return client.getQuota();
    }

    @Test
    void wordpressWithCapacityParsesEveryMember() {
        QuotaResponse q = quota(WORDPRESS_WITH_CAPACITY);
        assertEquals("pro", q.getPlan());
        assertEquals(1048576L, q.getBytesUsed());
        Capacity c = q.getCapacity();
        assertNotNull(c);
        assertEquals("2026-11-30T00:00:00Z", c.getPeriodEnd());
        assertEquals(75161927680L, c.getStorage().getLimitBytes());
        assertEquals(7516192768L, c.getStorage().getUsedBytes());
        assertEquals(53687091200L, c.getStorage().getBaseBytes());
        assertEquals(2L, c.getStorage().getAddonBlocks());
        assertEquals(53687091200L, c.getCdn().getLimitBytes());
        assertEquals(44023414784L, c.getCdn().getUsedBytes());
        assertEquals("warn_80", c.getCdn().getStatus());
        assertTrue(c.getBooster().isActive());
        assertEquals(2147483648L, c.getBooster().getWpQuotaBytes());
        assertNull(c.getBooster().getApiTransforms());
        assertEquals(107374182400L, c.getBooster().getCdnBytes());
        assertEquals("cdn_bytes", c.getBooster().getNextExpiry().getKind());
        assertEquals(42949672960L, c.getBooster().getNextExpiry().getUnits());
        assertEquals("2027-01-05T00:00:00Z", c.getBooster().getNextExpiry().getExpiresAt());
        assertNull(c.getLapse());
        assertEquals(BILLING, c.getManageUrl());
    }

    @Test
    void apiBlockedKeepsNullMembersNull() {
        Capacity c = quota(API_BLOCKED).getCapacity();
        assertNull(c.getPeriodEnd());
        assertEquals(107374182400L, c.getStorage().getLimitBytes());
        assertEquals(0L, c.getStorage().getAddonBlocks());
        assertNull(c.getCdn());
        assertFalse(c.getBooster().isActive());
        assertNull(c.getBooster().getWpQuotaBytes());
        assertEquals(5000L, c.getBooster().getApiTransforms());
        assertNull(c.getBooster().getCdnBytes());
        assertNull(c.getBooster().getNextExpiry());
        assertEquals("blocked", c.getLapse().getStage());
        assertEquals("2027-01-28T00:00:00Z", c.getLapse().getDeleteAfter());
        assertEquals(BILLING, c.getManageUrl());
    }

    @Test
    void sandboxHasZerosNotNulls() {
        Capacity c = quota(SANDBOX).getCapacity();
        assertNull(c.getStorage());
        assertEquals(1073741824L, c.getCdn().getLimitBytes());
        assertEquals(0L, c.getCdn().getUsedBytes());
        assertEquals("ok", c.getCdn().getStatus());
        assertFalse(c.getBooster().isActive());
        assertEquals(0L, c.getBooster().getApiTransforms());
        assertEquals(0L, c.getBooster().getCdnBytes());
        assertNull(c.getBooster().getWpQuotaBytes());
        assertNull(c.getLapse());
    }

    @Test
    void capacityNullAndAbsentBothParse() {
        QuotaResponse unreadable = quota(CAPACITY_UNREADABLE);
        assertEquals("pro", unreadable.getPlan());
        assertNull(unreadable.getCapacity());
        QuotaResponse older = quota(OLDER_SERVER_WITHOUT_CAPACITY);
        assertEquals("pro", older.getPlan());
        assertNull(older.getCapacity());
    }

    @Test
    void existingConstructorStillWorks() {
        assertNull(new QuotaResponse("pro", 1L, 2L, null, null, null, null, null).getCapacity());
    }

    private SmallPictException error(int status, String code, String detailsJson) {
        server.enqueue(new MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":\"" + code + "\",\"message\":\"m\",\"details\":" + detailsJson + "}}"));
        Throwable t = assertThrows(Throwable.class, () -> client.getQuota());
        return assertInstanceOf(SmallPictException.class, t, status + " " + code);
    }

    @Test
    void quotaErrorsCarryUpgradeUrl() {
        String[][] cases = {
                {"QUOTA_EXCEEDED", "{\"upgrade_url\":\"" + BILLING + "\"}"},
                {"storage_quota_exceeded", "{\"limit_bytes\":10,\"used_bytes\":10,\"remaining_bytes\":0,\"upgrade_url\":\"" + BILLING + "\"}"},
                {"account_read_only", "{\"delete_after\":\"2027-01-28T00:00:00Z\",\"upgrade_url\":\"" + BILLING + "\"}"},
        };
        for (String[] tc : cases) {
            SmallPictException e = error(402, tc[0], tc[1]);
            assertInstanceOf(QuotaExceededException.class, e, tc[0]);
            assertEquals(BILLING, e.getUpgradeUrl(), tc[0]);
        }
        SmallPictException readOnly = error(402, "account_read_only", "{\"delete_after\":\"2027-01-28T00:00:00Z\"}");
        assertEquals("2027-01-28T00:00:00Z", readOnly.getDetails().get("delete_after"));
    }

    @Test
    void quotaErrorWithoutUpgradeUrlIsNull() {
        SmallPictException e = error(402, "QUOTA_EXCEEDED", "{\"payload_bytes\":60000000}");
        assertInstanceOf(QuotaExceededException.class, e);
        assertNull(e.getUpgradeUrl());
    }

    @Test
    void modeNotInPlanCarriesAllowedModes() {
        SmallPictException e = error(403, "mode_not_in_plan", "{\"mode\":\"extreme\",\"allowed_modes\":[\"lossy\",\"lossless\"]}");
        PermissionDeniedException p = assertInstanceOf(PermissionDeniedException.class, e);
        assertEquals(Arrays.asList("lossy", "lossless"), p.getAllowedModes());
        assertNull(p.getUpgradeUrl());
        assertNull(new PermissionDeniedException("m").getAllowedModes());
    }

    @Test
    void conflictIsBaseExceptionWithCodeAndStatus() {
        SmallPictException e = error(409, "booster_period_limit_exceeded", "{\"remaining_steps\":0,\"upgrade_url\":\"" + BILLING + "\"}");
        assertEquals(SmallPictException.class, e.getClass());
        assertEquals("booster_period_limit_exceeded", e.getErrorCode());
        assertEquals(409, e.getStatusCode());
        assertEquals(BILLING, e.getUpgradeUrl());
    }

    @Test
    void nonStringUpgradeUrlIsNotExposed() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> details = mapper.convertValue(mapper.readTree("{\"upgrade_url\":42}"), Map.class);
        assertNull(new QuotaExceededException("m", null, details).getUpgradeUrl());
        assertNull(error(402, "QUOTA_EXCEEDED", "{\"upgrade_url\":42}").getUpgradeUrl());
    }

    @Test
    void typedExceptionsKeepTheServerCode() {
        assertEquals("account_read_only", error(402, "account_read_only", "{}").getErrorCode());
        assertEquals("purge_rate_limited", error(429, "purge_rate_limited", "{}").getErrorCode());
        assertEquals("invalid_folder", error(400, "invalid_folder", "{}").getErrorCode());
    }
}
