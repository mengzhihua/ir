package com.ir.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Component
public class TokenService {
    private final byte[] key;
    private final long ttl;
    public TokenService(@Value("${ir.auth.secret:}") String secret,
                        @Value("${ir.auth.token-ttl:12h}") Duration ttl) {
        if (secret == null || secret.trim().isEmpty()) {
            key = new byte[32]; new SecureRandom().nextBytes(key);
        } else key = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl.toMillis();
    }
    public String issue(User user) {
        String body = user.getId() + ":" + user.getUsername() + ":" + (System.currentTimeMillis() + ttl);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }
    public UserPrincipal parse(String token) {
        try {
            int dot = token == null ? -1 : token.lastIndexOf('.');
            if (dot <= 0) return null;
            String body = token.substring(0, dot);
            byte[] sig = Base64.getUrlDecoder().decode(token.substring(dot + 1));
            if (!MessageDigest.isEqual(sig, sign(body))) return null;
            String[] parts = new String(Base64.getUrlDecoder().decode(body), StandardCharsets.UTF_8).split(":", 3);
            long exp = Long.parseLong(parts[2]);
            return exp < System.currentTimeMillis() ? null : new UserPrincipal(Long.parseLong(parts[0]), parts[1]);
        } catch (Exception e) { return null; }
    }
    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static final class UserPrincipal {
        private final Long id; private final String username;
        UserPrincipal(Long id, String username) { this.id = id; this.username = username; }
        public Long getId() { return id; } public String getUsername() { return username; }
    }
}
