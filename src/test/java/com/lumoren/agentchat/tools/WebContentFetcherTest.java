package com.lumoren.agentchat.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebContentFetcherTest {

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("ftp://example.com/x"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("file:///etc/passwd"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("jar:file:///tmp/x.jar!/y"));
    }

    @Test
    void rejectsLocalhostNames() {
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://localhost/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://LOCALHOST:8080/"));
    }

    @Test
    void rejectsLoopbackAndPrivateLiterals() {
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://127.0.0.1/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://127.1.2.3/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://10.0.0.1/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://192.168.1.1/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://172.16.0.1/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://172.31.255.255/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://169.254.1.1/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://0.0.0.0/"));
    }

    @Test
    void rejectsIpv6LoopbackAndUniqueLocal() {
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://[::1]/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://[fd00::1]/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://[fc00::1]/"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://[fe80::1]/"));
    }

    @Test
    void rejectsMalformedUrls() {
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("not a url"));
        assertThrows(RuntimeException.class, () -> WebContentFetcher.validatePublicUrl("http://"));
    }

    @Test
    void acceptsPublicLiteralIps() {
        // Literal IPs skip DNS, so these are deterministic offline.
        assertDoesNotThrow(() -> WebContentFetcher.validatePublicUrl("http://8.8.8.8/"));
        assertDoesNotThrow(() -> WebContentFetcher.validatePublicUrl("https://1.1.1.1/"));
        assertDoesNotThrow(() -> WebContentFetcher.validatePublicUrl("https://[2001:4860:4860::8888]/"));
    }

    @Test
    void readBoundedAbortsOversizedBodies() throws Exception {
        java.io.InputStream big = new java.io.InputStream() {
            private long emitted;

            @Override
            public int read() {
                return emitted++ < 2_000_000 ? 'a' : -1;
            }
        };
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> invokeReadBounded(big, 1024));
        assertTrue(error.getMessage().contains("aborted"));
    }

    @Test
    void readBoundedReturnsSmallBodies() throws Exception {
        java.io.InputStream small = new java.io.ByteArrayInputStream("hello world".getBytes());
        Object result = invokeReadBounded(small, 1024);
        assertTrue(result instanceof String);
        assertTrue(((String) result).contains("hello world"));
    }

    private static Object invokeReadBounded(java.io.InputStream in, long max) throws Exception {
        var method = WebContentFetcher.class.getDeclaredMethod("readBounded", java.io.InputStream.class, long.class);
        method.setAccessible(true);
        try {
            return method.invoke(null, in, max);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw e;
        }
    }
}
