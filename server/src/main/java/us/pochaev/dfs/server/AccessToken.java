package us.pochaev.dfs.server;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * The secret in the printed link (DFS-036.AC-1): 256 random bits, URL-safe, valid for this server run.
 * It is compared in constant time and never logged except in the one "Open" line.
 */
@Component
class AccessToken {

    private final String value;

    AccessToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        value = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    String value() {
        return value;
    }

    boolean matches(String candidate) {
        return candidate != null && MessageDigest.isEqual(
                value.getBytes(StandardCharsets.US_ASCII), candidate.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public String toString() {
        return "AccessToken[hidden]";
    }
}
