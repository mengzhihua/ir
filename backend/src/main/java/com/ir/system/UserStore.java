package com.ir.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

@Service
public class UserStore {
    private final UserMapper mapper;

    public UserStore(UserMapper mapper) {
        this.mapper = mapper;
    }

    public User find(String username) {
        return mapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    public User get(Long id) {
        return mapper.selectById(id);
    }

    public User save(User user) {
        if (user.getId() == null) {
            mapper.insert(user);
        } else {
            mapper.updateById(user);
        }
        return user;
    }

    public static String hash(String value) {
        try {
            int iterations = 120000;
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            byte[] derived = derive(value, salt, iterations);
            return "pbkdf2$" + iterations + "$"
                    + Base64.getEncoder().encodeToString(salt) + "$"
                    + Base64.getEncoder().encodeToString(derived);
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算密码摘要", ex);
        }
    }

    public static boolean verify(String value, String stored) {
        if (value == null || stored == null) {
            return false;
        }
        if (stored.startsWith("pbkdf2$")) {
            try {
                String[] parts = stored.split("\\$", -1);
                if (parts.length != 4) {
                    return false;
                }
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = Base64.getDecoder().decode(parts[2]);
                byte[] expected = Base64.getDecoder().decode(parts[3]);
                return MessageDigest.isEqual(expected,
                        derive(value, salt, iterations));
            } catch (Exception ex) {
                return false;
            }
        }
        return stored.matches("[0-9a-fA-F]{64}")
                && legacyHash(value).equalsIgnoreCase(stored);
    }

    public static boolean isLegacy(String stored) {
        return stored != null && stored.matches("[0-9a-fA-F]{64}");
    }

    private static byte[] derive(
            String value,
            byte[] salt,
            int iterations) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(
                value.toCharArray(), salt, iterations, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).getEncoded();
    }

    private static String legacyHash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte valueByte : bytes) {
                result.append(String.format("%02x", valueByte));
            }
            return result.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算密码摘要", ex);
        }
    }
}
