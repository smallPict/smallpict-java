package com.smallpict;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CryptoTest {

    @Test
    void testEmptySha256Constant() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Crypto.EMPTY_SHA256);
        assertEquals(Crypto.EMPTY_SHA256, Crypto.sha256Hex(new byte[0]));
        assertEquals(Crypto.EMPTY_SHA256, Crypto.sha256Hex(null));
    }

    @Test
    void testSha256Hex() {
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
                Crypto.sha256Hex("hello world".getBytes()));
    }

    @Test
    void testBuildStringToSign() {
        String sts = Crypto.buildStringToSign("POST", "/v1/optimize", "1716301234", Crypto.EMPTY_SHA256);
        assertEquals("POST\n/v1/optimize\n1716301234\n" + Crypto.EMPTY_SHA256, sts);
    }

    @Test
    void testHmacSha256Hex() {
        String sts = Crypto.buildStringToSign("POST", "/v1/optimize", "1716301234", Crypto.EMPTY_SHA256);
        String sig = Crypto.hmacSha256Hex("sec_test_secret_123", sts);
        assertNotNull(sig);
        assertEquals(64, sig.length());
        assertTrue(sig.matches("^[a-f0-9]{64}$"));
    }

    @Test
    void hmacSha256KnownAnswerRfc4231Case2() {
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                Crypto.hmacSha256Hex("Jefe", "what do ya want for nothing?"));
    }

    @Test
    void stringToSignFollowsTheSpec() {
        assertEquals("POST\n/v1/optimize\n1716301234\n" + Crypto.EMPTY_SHA256,
                Crypto.buildStringToSign("POST", "/v1/optimize", "1716301234", Crypto.EMPTY_SHA256));
    }
}
