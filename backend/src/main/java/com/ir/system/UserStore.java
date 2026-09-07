package com.ir.system;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UserStore {
    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    @Value("${ir.auth.admin-password:admin123}") private String adminPassword;

    @PostConstruct
    public void init() {
        User u = new User();
        u.setId(ids.incrementAndGet()); u.setUsername("admin"); u.setPassword(hash(adminPassword));
        u.setRealName("系统管理员"); u.setRole(User.ADMIN); users.put(u.getUsername(), u);
    }
    public User find(String username) { return users.get(username); }
    public User get(Long id) { return users.values().stream().filter(u -> u.getId().equals(id)).findFirst().orElse(null); }
    public static String hash(String value) {
        try {
            byte[] out = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : out) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
